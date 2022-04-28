package com.example.ffmpeg.service;

import com.example.ffmpeg.config.ConfigBean;
import com.example.ffmpeg.m3u8.M3u8DownloadFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WebService {

    @Autowired
    ConfigBean configBean;

    @Async
    public void exe(String code) {
        try {
            String requestUrl = configBean.fileSourcePath.replace("{code}", code);
            String outPath = configBean.fileOutPath.replace("{code}", code);
            System.out.println(" 开始下载： " + code + ", " + requestUrl);
            M3u8DownloadFactory.M3u8Download m3u8Download = M3u8DownloadFactory.getInstance(requestUrl);
            //设置生成目录
            m3u8Download.setDir(outPath);
            m3u8Download.setFfmpegPath(configBean.fileFfmpegPath);
            //设置视频名称
            m3u8Download.setFileName(code);
            //设置重试次数
            m3u8Download.setRetryCount(10);
            //设置连接超时时间（单位：毫秒）
            m3u8Download.setTimeoutMillisecond(10000L);
            //设置监听器间隔（单位：毫秒）
            m3u8Download.setInterval(20000L);
            //开始下载
            m3u8Download.start();
        } catch (Exception e) {
            log.error(e);

        }
    }
}
