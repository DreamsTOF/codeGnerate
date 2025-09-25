package com.dream.codegenerate.utils;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.dream.codegenerate.exception.BusinessException;
import com.dream.codegenerate.exception.ErrorCode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.UUID;

/**
 * 截图工具类（使用WebDriver对象池）
 */
@Slf4j
@Component // 改为Spring Bean，方便管理生命周期
public class WebScreenshotUtils {

    // WebDriver对象池
    private final GenericObjectPool<WebDriver> webDriverPool;

    // 构造函数中初始化对象池
    public WebScreenshotUtils() {
        // 创建对象池工厂
        WebDriverFactory webDriverFactory = new WebDriverFactory();

        // 配置对象池
        GenericObjectPoolConfig<WebDriver> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(10); // 最大实例数
        config.setMinIdle(5);   // 核心（常驻）实例数
        config.setMaxIdle(8);   // 最大空闲实例数
        config.setTestOnBorrow(true); // 借出时测试连接有效性
        config.setTestOnReturn(true); // 归还时测试
        config.setBlockWhenExhausted(true); // 当池子耗尽时，阻塞等待，而不是抛异常
        config.setMaxWait(Duration.ofMillis(10000)); // 最大等待时间10秒

        // 初始化对象池
        this.webDriverPool = new GenericObjectPool<>(webDriverFactory, config);
        log.info("WebDriver对象池初始化完成，MinIdle=5, MaxTotal=10");
    }

    /**
     * Spring容器销毁前，关闭对象池
     */
    @PreDestroy
    public void destroy() {
        if (this.webDriverPool != null) {
            this.webDriverPool.close();
            log.info("WebDriver对象池已关闭。");
        }
    }

    /**
     * 生成网页截图
     *
     * @param webUrl 要截图的网址
     * @return 压缩后的截图文件路径，失败返回 null
     */
    public  String saveWebPageScreenshot(String webUrl) {
        if (StrUtil.isBlank(webUrl)) {
            log.error("网页截图失败，url为空");
            return null;
        }

        WebDriver webDriver = null;
        try {
            // 1. 从池中借用一个WebDriver实例
            log.info("尝试从池中获取WebDriver实例... Active: {}, Idle: {}", webDriverPool.getNumActive(), webDriverPool.getNumIdle());
            webDriver = webDriverPool.borrowObject();
            log.info("成功获取WebDriver实例。Active: {}, Idle: {}", webDriverPool.getNumActive(), webDriverPool.getNumIdle());


            // 创建临时目录
            String rootPath = System.getProperty("user.dir") + "/tmp/screenshots/" + UUID.randomUUID().toString().substring(0, 8);
            FileUtil.mkdir(rootPath);
            final String IMAGE_SUFFIX = ".png";
            String imageSavePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + IMAGE_SUFFIX;

            // 访问网页
            webDriver.get(webUrl);
            waitForPageLoad(webDriver);

            // 截图
            byte[] screenshotBytes = ((TakesScreenshot) webDriver).getScreenshotAs(OutputType.BYTES);
            saveImage(screenshotBytes, imageSavePath);
            log.info("原始截图保存成功：{}", imageSavePath);

            // 压缩图片
            final String COMPRESS_SUFFIX = "_compressed.jpg";
            String compressedImagePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + COMPRESS_SUFFIX;
            compressImage(imageSavePath, compressedImagePath);
            log.info("压缩图片保存成功：{}", compressedImagePath);

            FileUtil.del(imageSavePath);
            return compressedImagePath;
        } catch (Exception e) {
            log.error("网页截图失败：{}", webUrl, e);
            // 如果出现异常，可能实例已损坏，从池中移除
            if (webDriver != null) {
                try {
                    webDriverPool.invalidateObject(webDriver);
                    webDriver = null; // 防止finally块中再次归还
                } catch (Exception ex) {
                    log.error("使WebDriver实例失效时出错", ex);
                }
            }
            return null;
        } finally {
            // 2. 确保将WebDriver实例归还到池中
            if (webDriver != null) {
                webDriverPool.returnObject(webDriver);
                log.info("WebDriver实例已归还到池中。Active: {}, Idle: {}", webDriverPool.getNumActive(), webDriverPool.getNumIdle());
            }
        }
    }


    /**
     * WebDriver 工厂类，用于对象池创建、验证和销毁WebDriver实例
     */
    private static class WebDriverFactory extends BasePooledObjectFactory<WebDriver> {

        private static final String CHROME_DRIVER_LINUX_RESOURCE_PATH = "drivers/chromedriver";
        private static final int DEFAULT_WIDTH = 1600;
        private static final int DEFAULT_HEIGHT = 900;
        private volatile String chromeDriverPath; // 使用volatile确保多线程可见性

        public WebDriverFactory() {
            // 在工厂构造时，就准备好驱动路径
            setupDriverPath();
        }

        /**
         * 准备驱动路径，如果是Linux则从JAR包解压
         */
        private void setupDriverPath() {
            try {
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    // Windows环境下，直接使用classpath下的exe（适用于开发环境）
                    File driverFile = new File(getClass().getClassLoader().getResource("drivers/chromedriver.exe").toURI());
                    this.chromeDriverPath = driverFile.getAbsolutePath();
                } else {
                    // Linux环境下，从JAR包中提取chromedriver到临时文件
                    try (InputStream in = getClass().getClassLoader().getResourceAsStream(CHROME_DRIVER_LINUX_RESOURCE_PATH)) {
                        if (in == null) {
                            throw new IllegalStateException("在classpath中找不到ChromeDriver: " + CHROME_DRIVER_LINUX_RESOURCE_PATH);
                        }
                        File tempFile = File.createTempFile("chromedriver", "");
                        try (OutputStream out = new FileOutputStream(tempFile)) {
                            FileCopyUtils.copy(in, out);
                        }
                        // 关键步骤：设置执行权限
                        if (!tempFile.setExecutable(true)) {
                            log.warn("无法为ChromeDriver设置执行权限: {}", tempFile.getAbsolutePath());
                        }
                        // JVM退出时自动删除
                        tempFile.deleteOnExit();
                        this.chromeDriverPath = tempFile.getAbsolutePath();
                        log.info("ChromeDriver (Linux) 已解压到临时文件: {}", this.chromeDriverPath);
                    }
                }
                // 设置系统属性
                System.setProperty("webdriver.chrome.driver", this.chromeDriverPath);
            } catch (Exception e) {
                log.error("初始化ChromeDriver路径失败", e);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化ChromeDriver路径失败");
            }
        }


        // 创建一个新的WebDriver实例
        @Override
        public WebDriver create() {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless", "--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage", "--disable-extensions");
            options.addArguments(String.format("--window-size=%d,%d", DEFAULT_WIDTH, DEFAULT_HEIGHT));
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

            WebDriver driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            log.info("成功创建一个新的WebDriver实例。");
            return driver;
        }

        // 用于池化对象
        @Override
        public PooledObject<WebDriver> wrap(WebDriver driver) {
            return new DefaultPooledObject<>(driver);
        }

        // 销毁一个WebDriver实例（当实例从池中被移除时调用）
        @Override
        public void destroyObject(PooledObject<WebDriver> p) {
            WebDriver driver = p.getObject();
            if (driver != null) {
                driver.quit();
                log.info("一个WebDriver实例已被销毁。");
            }
        }

        // 验证一个WebDriver实例是否仍然有效（在借用或归还时调用）
        @Override
        public boolean validateObject(PooledObject<WebDriver> p) {
            WebDriver driver = p.getObject();
            try {
                // 尝试获取当前窗口句柄，如果浏览器已崩溃或关闭，会抛异常
                driver.getWindowHandles();
                return true;
            } catch (Exception e) {
                log.warn("WebDriver实例验证失败，可能已失效。", e);
                return false;
            }
        }
    }


    // 以下是原有的辅助方法，保持不变
    private static void saveImage(byte[] imageBytes, String imagePath) {
        try {
            FileUtil.writeBytes(imageBytes, imagePath);
        } catch (Exception e) {
            log.error("保存图片失败：{}", imagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存图片失败");
        }
    }

    private static void compressImage(String originImagePath, String compressedImagePath) {
        final float COMPRESSION_QUALITY = 0.3f;
        try {
            ImgUtil.compress(
                    FileUtil.file(originImagePath),
                    FileUtil.file(compressedImagePath),
                    COMPRESSION_QUALITY
            );
        } catch (Exception e) {
            log.error("压缩图片失败：{} -> {}", originImagePath, compressedImagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "压缩图片失败");
        }
    }

    private static void waitForPageLoad(WebDriver webDriver) {
        try {
            WebDriverWait wait = new WebDriverWait(webDriver, Duration.ofSeconds(10));
            wait.until(driver -> ((JavascriptExecutor) driver).executeScript("return document.readyState").equals("complete"));
            Thread.sleep(2000); // 建议保留，等待JS动态内容
        } catch (Exception e) {
            log.error("等待页面加载时出现异常，继续执行截图", e);
        }
    }
}
