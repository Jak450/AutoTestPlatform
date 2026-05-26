package org.example.ai_study_notes.POM.page;

import lombok.extern.slf4j.Slf4j;
import org.example.ai_study_notes.Pojo.dto.UiDTO.UiAssertionDTO;
import org.example.ai_study_notes.Pojo.dto.UiDTO.UiTestStepDTO;
import org.example.ai_study_notes.Pojo.vo.UiVO.UiStepResultVO;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Slf4j
public class BasePage {

    private final WebDriver driver;
    private final WebDriverWait wait;
     Actions action  =null;

    public BasePage(WebDriver driver, Duration timeout) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, timeout);
        action = new Actions(driver);
    }

    public UiStepResultVO executeStep(UiTestStepDTO step) {
        long start = System.currentTimeMillis();
        UiStepResultVO.UiStepResultVOBuilder builder = UiStepResultVO.builder()
                .name(StringUtils.hasText(step.getName()) ? step.getName() : step.getAction())
                .action(step.getAction());
        try {
            String actualValue = performAction(step);
            validateAssertion(step.getAssertion(), actualValue);
            if (step.getWaitTime() != null && step.getWaitTime() > 0) {
                Thread.sleep(step.getWaitTime());
            }
            builder.status("passed")
                    .actualValue(actualValue)
                    .duration(System.currentTimeMillis() - start);
        } catch (Exception e) {
            builder.status("failed")
                    .message(e.getMessage())
                    .duration(System.currentTimeMillis() - start)
                    .screenshotBase64(captureScreenshot());
            log.error("步骤 {} failed", step.getName(), e);
        }
        return builder.build();
    }

    private String performAction(UiTestStepDTO step) {
        String action = step.getAction() == null ? "" : step.getAction().toLowerCase();
        switch (action) {
            case "click":
                locate(step, true).click();
                return null;
            case "input":
                WebElement input = locate(step, true);
                input.clear();
                input.sendKeys(StringUtils.hasText(step.getActionValue()) ? step.getActionValue() : "");
                return null;
            case "gettext":
                return locate(step, true).getText();
            case "getattribute":
                WebElement element = locate(step, true);
                String attribute = StringUtils.hasText(step.getActionValue()) ? step.getActionValue() : "value";
                return element.getAttribute(attribute);
            case "hover":
                new Actions(driver).moveToElement(locate(step, true)).perform();
                return null;
            case "waitvisible":
                wait.until(ExpectedConditions.visibilityOfElementLocated(buildBy(step)));
                return null;
            case "waithidden":
                wait.until(ExpectedConditions.invisibilityOfElementLocated(buildBy(step)));
                return null;
            case "scroll":
                scroll(step.getActionValue());
                return null;
            case "screenshot":
                return captureScreenshot();
            case "switchwindow":
                 switchWindow(step.getActionValue());
                 return null;
            case "customcode":
                executeCustomCode(step);
                return null;
            default:
                throw new UnsupportedOperationException("暂不支持的操作类型: " + step.getAction());
        }
    }

    //元素定位
    private WebElement locate(UiTestStepDTO step, boolean required) {
        By by = buildBy(step);
        if (required) {
            wait.until(ExpectedConditions.presenceOfElementLocated(by));
        }
        return driver.findElement(by);
    }

    //定位类型
    private By buildBy(UiTestStepDTO step) {
        String locatorType = step.getLocatorType();
        String locatorValue = step.getLocatorValue();
        if (!StringUtils.hasText(locatorValue)) {
            throw new IllegalArgumentException("定位值不能为空");
        }
        String type = StringUtils.hasText(locatorType) ? locatorType.toLowerCase() : "css";
        switch (type) {
            case "id":
                return By.id(locatorValue);
            case "name":
                return By.name(locatorValue);
            case "xpath":
                return By.xpath(locatorValue);
            case "text":
                return By.xpath(String.format("//*[contains(text(),'%s')]", locatorValue));
            case "css":
            default:
                return By.cssSelector(locatorValue);
        }
    }


    public void enterFrame(String frameID) {
        driver.switchTo().frame(frameID);
        log.info("切换至{}内嵌窗口",frameID);
    }


    //根据窗口句柄切换
    private void switchWindow(String windowID) {
        driver.switchTo().window(windowID);
        log.info("切换至{}外部窗口",windowID);
    }

    //执行回车，要有前提目标元素，不单用
    public void enter() {
        action.sendKeys(Keys.ENTER);
    }


    //接受提示框
    public void alertAccept() {
        Alert alert = driver.switchTo().alert();
        alert.accept();

    }


    //双击
    public void doubleClick(UiTestStepDTO step) throws InterruptedException {
     WebElement element =  locate(step, true);

        action.doubleClick(element).build().perform();
    }

    //鼠标右击
    private void ContextClick(UiTestStepDTO step)
    {
        WebElement element =  locate(step, true);
        action.contextClick(element).build().perform();

    }

    //点击并按住
    private void ClickAndHold(UiTestStepDTO step)
    {
        WebElement element =  locate(step, true);
        action.clickAndHold(element).perform();
    }

    //释放之前按住的鼠标左键
    private void Release()
    {

        action.release().perform();
    }


    private void DragAndDrop(UiTestStepDTO step)
    {

    }




    private void scroll(String value) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        if (!StringUtils.hasText(value)) {
            js.executeScript("window.scrollTo(0, document.body.scrollHeight)");
            return;
        }
        switch (value.toLowerCase()) {
            case "top":
                js.executeScript("window.scrollTo(0, 0)");
                break;
            case "bottom":
                js.executeScript("window.scrollTo(0, document.body.scrollHeight)");
                break;
            default:
                int pixels = Integer.parseInt(value);
                js.executeScript("window.scrollBy(0, arguments[0])", pixels);
        }
    }


    //自定义脚本扩展
    private void executeCustomCode(UiTestStepDTO step) {
        String script = StringUtils.hasText(step.getCustomCode()) ? step.getCustomCode() : step.getActionValue();
        if (!StringUtils.hasText(script)) {
            throw new IllegalArgumentException("自定义扩展需要提供可执行的脚本");
        }
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript(script);
    }

    //参数校验
    private void validateAssertion(UiAssertionDTO assertion, String actualValue) {
        if (assertion == null) {
            return;
        }
        String type = assertion.getType() == null ? "" : assertion.getType().toLowerCase();
        switch (type) {
            case "equals":
                if (!StringUtils.hasText(actualValue) || !actualValue.equals(assertion.getExpected())) {
                    throw new AssertionError(String.format("期望值 %s ，实际值 %s", assertion.getExpected(), actualValue));
                }
                break;
            case "contains":
                if (!StringUtils.hasText(actualValue) || !actualValue.contains(assertion.getExpected())) {
                    throw new AssertionError(String.format("期望包含 %s ，实际值 %s", assertion.getExpected(), actualValue));
                }
                break;
            case "notempty":
                if (!StringUtils.hasText(actualValue)) {
                    throw new AssertionError("期望值不为空，但实际为空");
                }
                break;
            default:
                throw new UnsupportedOperationException("不支持的断言类型: " + assertion.getType());
        }
    }

    //截图
    private String captureScreenshot() {
        if (!(driver instanceof TakesScreenshot)) {
            return null;
        }
        return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BASE64);
    }
}

