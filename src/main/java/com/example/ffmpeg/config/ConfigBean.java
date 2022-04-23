package com.example.ffmpeg.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
public class ConfigBean {

    @Value("${file.out.path}")
    public String fileOutPath;

    @Value("${file.source.path}")
    public String fileSourcePath;

    @Value("${file.ffmpeg.path}")
    public String fileFfmpegPath;
}
