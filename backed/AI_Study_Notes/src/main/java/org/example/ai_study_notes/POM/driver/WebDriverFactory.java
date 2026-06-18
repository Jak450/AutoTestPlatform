package org.example.ai_study_notes.POM.driver;

import org.example.ai_study_notes.Pojo.dto.UiDTO.UiTestCaseRequestDTO;
import org.example.ai_study_notes.utils.ReadProperties;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class WebDriverFactory {

    public static WebDriver create(UiTestCaseRequestDTO request) throws IOException {
        String browser = StringUtils.hasText(request.getBrowser()) ?
                request.getBrowser().toLowerCase() : "chrome";
        boolean headless = request.getHeadless() == null || request.getHeadless();

        WebDriver driver;
        switch (browser) {
            case "firefox":
                String firefox_driver = ReadProperties.getPropertyValue("gecko_driver");
                String firefox_path = ReadProperties.getPropertyValue("firefox_path");
                if (StringUtils.hasText(firefox_driver)) {
                    System.setProperty("webdriver.gecko.driver", firefox_driver);
                }
                if (StringUtils.hasText(firefox_path)) {
                    System.setProperty("webdriver.firefox.bin", firefox_path);
                }
                FirefoxOptions firefoxOptions = new FirefoxOptions();
                if (headless) {
                    firefoxOptions.addArguments("-headless");
                }
                driver = new FirefoxDriver(firefoxOptions);
                break;
            case "edge":
                String edge_path = ReadProperties.getPropertyValue("edge_path");
                if (StringUtils.hasText(edge_path)) {
                    System.setProperty("webdriver.edge.driver", edge_path);
                }
                EdgeOptions edgeOptions = new EdgeOptions();
                if (headless) {
                    edgeOptions.addArguments("--headless=new");
                }
                driver = new EdgeDriver(edgeOptions);
                break;
            case "chrome":
            default:
                String chrome_path = ReadProperties.getPropertyValue("chrome_path");
                if (StringUtils.hasText(chrome_path) && Files.exists(Paths.get(chrome_path))) {
                    System.setProperty("webdriver.chrome.driver", chrome_path);
                }
                ChromeOptions chromeOptions = new ChromeOptions();
                if (headless) {
                    chromeOptions.addArguments("--headless=new");
                }
                chromeOptions.addArguments("--disable-gpu");
                chromeOptions.addArguments("--no-sandbox");
                chromeOptions.addArguments("--disable-dev-shm-usage");
                driver = new ChromeDriver(chromeOptions);
        }

        applyViewport(driver, request.getViewport());
        return driver;
    }

    private static void applyViewport(WebDriver driver, String viewport) {
        if (!StringUtils.hasText(viewport) || viewport.split("x").length != 2) {
            return;
        }
        try {
            String[] parts = viewport.toLowerCase().split("x");
            int width = Integer.parseInt(parts[0].trim());
            int height = Integer.parseInt(parts[1].trim());
            driver.manage().window().setSize(new Dimension(width, height));
        } catch (NumberFormatException ignored) {
        }
    }
}

