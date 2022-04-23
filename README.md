# downM3U8

基于springboot + ffmpeg 下载m3u8链接。

## 可以下载M3U8格式的链接。转换成mp4格式

1. 通过获取m3u8链接 , 进行并发下载TS文件
2. 通过ffmpeg 将TS文件拼成MP4文件

## 使用方式

下载到本地地址：file.out.path=F:\\360Downloads\\mo
下载地址： file.source.path=https://xxxxxx/m3u8/{code}/{code}.m3u8
ffmpeg本地地址：file.ffmpeg.path=F:\\360Downloads\\mo\\ffmpeg\\bin\\ffmpeg

请求方式: localhost:7888/codes/?codes=xxx

> 如果 code是一个URL的话。。 自己改一下代码。 
