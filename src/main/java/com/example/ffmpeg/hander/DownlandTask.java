package com.example.ffmpeg.hander;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.LinkedBlockingQueue;

@Component
@Slf4j
public class DownlandTask {


    public static LinkedBlockingQueue<String> WAIT_QUEUE = new LinkedBlockingQueue<>();


    @Autowired
    public WebHandler webHandler;

    @Scheduled(fixedDelay = 1000)
    public void run() {
        log.info("开始执行 下载文件队列扫描 ");
        String poll = null;
        try {
            poll = WAIT_QUEUE.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        webHandler.exe(poll);
        log.info("执行完毕 下载文件队列扫描 ");

    }
    


}
