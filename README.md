# downM3U8

基于springboot + ffmpeg 下载m3u8链接。 【下载的视频请勿用于商业行为】 ，自己看就好了。

## 可以下载M3U8格式的链接。转换成mp4格式

1. 通过获取m3u8链接 , 进行并发下载TS文件
2. 通过ffmpeg 将TS文件拼成MP4文件

## 使用方式

请求方式get: http://localhost:7888/codes/?codes=xxx

## 配置方式

下载到本地地址：file.out.path=F:\\xxxx

下载地址： file.source.path=https://xxxxxx/m3u8/{code}/{code}.m3u8 【可以下载某1的视频】

ffmpeg本地地址：file.ffmpeg.path=F:\\xxxxx\\ffmpeg\\bin\\ffmpeg

> 如果 code是一个URL的话。。 自己改一下代码。  
