package org.example.ai_study_notes.utils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ReadProperties {

	// 从 java 目录改为 resources 目录
	public static final String RESOURCE_PATH = "UI_conf/config.properties";


	public static String getPropertyValue(String key) throws IOException {
		Properties prop = new Properties();
		// 用类加载器加载 classpath 中的资源（自动适配开发/打包环境）
		try (InputStream is = ReadProperties.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
			if (is == null) {
				throw new FileNotFoundException("配置文件 " + RESOURCE_PATH + " 不存在于classpath中");
			}
			prop.load(is);
			return prop.getProperty(key);
		}
	}
}
