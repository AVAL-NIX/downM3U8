package com.example.ffmpeg.m3u8;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.http.HttpRequest;
import lombok.Data;
import org.apache.commons.lang3.math.NumberUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.*;
import java.math.BigDecimal;
import java.security.Security;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Data
public class M3u8DownloadFactory {

    static ReentrantLock lock = new ReentrantLock();
    private static M3u8Download m3u8Download;

    public static final String FILESEPARATOR = System.getProperty("file.separator");

    @Data
    public static class M3u8Download {

        //要下载的m3u8链接
        private String DOWNLOADURL;

        private String ffmpegPath;

        //优化内存占用
        private static final BlockingQueue<byte[]> BLOCKING_QUEUE = new LinkedBlockingQueue<>();

        //线程数
        private int threadCount = 30;

        //重试次数
        private int retryCount = 30;

        //链接连接超时时间（单位：毫秒）
        private long timeoutMillisecond = 1000L;

        //合并后的文件存储目录
        private String dir;

        private Set<String> requestQueue = ConcurrentHashMap.newKeySet();

        //合并后的视频文件名称
        private String fileName;

        //已完成ts片段个数
        private int finishedCount = 0;

        //所有ts片段下载链接
        private ConcurrentHashMap<String, String> tsSet = new ConcurrentHashMap<String, String>();

        //解密后的片段
        private Set<File> finishedFiles = new ConcurrentSkipListSet<>();

        //已经下载的文件大小
        private BigDecimal downloadBytes = new BigDecimal(0);

        //监听间隔
        private long interval = 0L;

        /**
         * 开始下载视频
         */
        public void start() {
            checkField();
            getTsUrl();
            startDownload();
        }


        /**
         * 下载视频
         */
        private void startDownload() {
            //线程池
            final ExecutorService fixedThreadPool = Executors.newFixedThreadPool(threadCount);
            int i = 0;
            //如果生成目录不存在，则创建
            File file1 = new File(dir);
            if (!file1.exists())
                file1.mkdirs();
            //执行多线程下载
            Set<Entry<String, String>> entries = tsSet.entrySet();
            for (Entry<String, String> entry : entries) {
                fixedThreadPool.execute(getThread(entry.getKey(), entry.getValue()));
            }
            fixedThreadPool.shutdown();
            //下载过程监视
            long beginTime = System.currentTimeMillis();
            new Thread(() -> {
                int consume = 0;
                //轮询是否下载成功
                while (!fixedThreadPool.isTerminated()) {
                    try {
                        consume++;
                        BigDecimal bigDecimal = new BigDecimal(downloadBytes.toString());
                        Thread.sleep(interval);
                        System.out.println("正在下载队列：");
                        for (String s : requestQueue) {
                            System.out.println(s);
                        }
                        System.out.println("已用时" + (System.currentTimeMillis() - beginTime) / 1000 + "秒！\t下载速度：" + StringUtils.convertToDownloadSpeed(new BigDecimal(downloadBytes.toString()).subtract(bigDecimal), 3) + "/s");
                        System.out.println("\t已完成" + finishedCount + "个，还剩" + (tsSet.size() - finishedCount) + "个！");
                        System.out.println(new BigDecimal(finishedCount).divide(new BigDecimal(tsSet.size()), 4, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100)).setScale(2, BigDecimal.ROUND_HALF_UP) + "%");

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

            new Thread(() -> {
                int consume = 0;
                //轮询是否下载成功
                while (!fixedThreadPool.isTerminated()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                System.out.println("下载完成，正在合并文件！共" + finishedFiles.size() + "个！" + StringUtils.convertToDownloadSpeed(downloadBytes, 3));
                //开始合并视频
                mergeTs();
                //删除多余的ts片段
                deleteFiles();
                System.out.println("视频合并完成，欢迎使用!");
            }).start();
        }

        /**
         * 合并下载好的ts片段 , 由FFMPEG合并
         */
        private void mergeTs() {
            try {
                String output = dir + FILESEPARATOR + fileName + ".mp4";
                System.gc();
                File file = new File(dir + FILESEPARATOR + fileName);
                List<String> sb = new ArrayList<>();
                sb.add(ffmpegPath);
                sb.add(" -i ");
                sb.add(" \"");
                sb.add("concat:");
                File[] files = file.listFiles();
                //排序保证文件顺序
                List<File> result = Arrays.stream(files).sorted((o1, o2) -> {
                    boolean b = NumberUtils.toInt(o1.getName().substring(0, o1.getName().lastIndexOf("."))) > NumberUtils.toInt(o2.getName().substring(0, o2.getName().lastIndexOf(".")));
                    if (b) {
                        return 1;
                    }
                    return -1;
                }).collect(Collectors.toList());
                for (File f : result) {
                    String name = f.getAbsolutePath();
                    sb.add(name);
                    sb.add("|");
                }
                sb.add("\" ");
                sb.add(" -c ");
                sb.add(" copy ");
                sb.add(" ");
                sb.add(output);
                commonStar(sb);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        /**
         * 执行命令
         *
         * @param commons
         */
        private void commonStar(List<String> commons) {
            try {
                lock.lock();
                System.out.println("CMD命令：" + fileName);

                System.out.println(org.apache.commons.lang3.StringUtils.join(commons, ""));

                Process exec = Runtime.getRuntime().exec(org.apache.commons.lang3.StringUtils.join(commons, ""));

                dealStream(exec);

                exec.waitFor(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }

        /**
         * 处理process输出流和错误流，防止进程阻塞
         * <p>
         * 在process.waitFor();前调用
         *
         * @param process
         */
        private static void dealStream(Process process) {
            if (process == null) {
                return;
            }
            new Thread() {
                @Override
                public void run() {
                    BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line = null;
                    try {
                        while ((line = in.readLine()) != null) {
                            System.out.println(" info " + line);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            in.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }.start();

            new Thread() {
                @Override
                public void run() {
                    BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    String line = null;
                    try {
                        while ((line = err.readLine()) != null) {
                            System.out.println(" error " + line);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            err.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }.start();

        }


        /**
         * 删除下载好的片段
         */
        private void deleteFiles() {
//            File file = new File(dir + FILESEPARATOR + fileName);
//            for (File listFile : file.listFiles()) {
//                listFile.delete();
//            }
//            file.delete();
        }

        /**
         * 开启下载线程
         *
         * @param urls ts片段链接
         * @param name ts片段序号
         * @return 线程
         */
        private Thread getThread(String urls, String name) {
            return new Thread(() -> {
                int count = 1;
                //xy为未解密的ts片段，如果存在，则删除
                String filePath = dir + FILESEPARATOR + fileName + FILESEPARATOR + name;
                File file2 = new File(filePath);
                System.out.println(" 开始下载： " + urls + " , 文件路径：" + file2);
                if (file2.exists())
                    file2.delete();
                FileOutputStream outputStream1 = null;
                //重试次数判断
                while (count <= retryCount) {
                    try {
                        //链式构建请求
                        requestQueue.add(urls);
                        byte[] resultByte = HttpRequest.get(urls).timeout((int) timeoutMillisecond)//超时，毫秒
                                .execute().bodyBytes();
                        requestQueue.remove(urls);
                        downloadBytes = downloadBytes.add(new BigDecimal(resultByte.length));
                        File file = new File(filePath);
                        FileUtil.writeBytes(resultByte, file);
                        finishedFiles.add(file);
                        break;
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.out.println("第" + count + "获取链接重试！\t" + urls);
                        count++;
                    }
                }
                if (count > retryCount)
                    //自定义异常
                    throw new M3u8Exception("10次重试失败，连接超时！");
                finishedCount++;
                System.out.println(urls + "下载完毕！\t已完成" + finishedCount + "个，还剩" + (tsSet.size() - finishedCount) + "个！");
            });
        }

        /**
         * 获取所有的ts片段下载链接
         *
         * @return 链接是否被加密，null为非加密
         */
        private void getTsUrl() {
            StringBuilder content = getUrlContent(DOWNLOADURL);
            //判断是否是m3u8链接
            System.out.println(" 获取的到URL 内容： " + content);
            if (!content.toString().contains("#EXTM3U"))
                throw new M3u8Exception(DOWNLOADURL + "不是m3u8链接！");
            String[] split = content.toString().split("\\n");
            String downUrl = "";
            boolean isKey = false;
            for (String s : split) {
                //如果含有此字段，则说明只有一层m3u8链接
                if (s.contains("#EXT-X-KEY") || s.contains("#EXTINF")) {
                    isKey = true;
                    downUrl = DOWNLOADURL;
                    break;
                }
                //如果含有此字段，则说明ts片段链接需要从第二个m3u8链接获取
                if (s.contains(".m3u8")) {
                    if (StringUtils.isUrl(s)) {
                        System.out.println(" 第2个片段是HTTP URL ");
                        return;
                    }
                    String relativeUrl = DOWNLOADURL.substring(0, DOWNLOADURL.lastIndexOf("/") + 1);
                    if (s.startsWith("/"))
                        s = s.replaceFirst("/", "");
                    downUrl = mergeUrl(relativeUrl, s);
                    break;
                }
            }

            if (StringUtils.isEmpty(downUrl))
                throw new M3u8Exception("未发现下载链接！");
            //获取KEY
            getKey(downUrl, content);
        }

        /**
         * 模拟http请求获取内容
         *
         * @param urls http链接
         * @return 内容
         */
        private StringBuilder getUrlContent(String urls) {
            int count = 1;
            StringBuilder content = new StringBuilder();
            while (count <= retryCount) {
                try {
                    byte[] resultByte = HttpRequest.get(urls).timeout((int) timeoutMillisecond)//超时，毫秒
                            .execute().bodyBytes();
                    content.append(new String(resultByte));
                    return content;
                } catch (Exception e) {
                    System.out.println("第" + count + "获取链接重试！\t" + urls);
                    count++;
                }
            }
            if (count > retryCount)
                throw new M3u8Exception("连接超时！");
            return content;
        }


        /**
         * 获取ts解密的密钥，并把ts片段加入set集合
         *
         * @param url     密钥链接，如果无密钥的m3u8，则此字段可为空
         * @param content 内容，
         */
        private void getKey(String url, StringBuilder content) {
            StringBuilder urlContent = content;
            String[] split = urlContent.toString().split("\\n");
            String relativeUrl = url.substring(0, url.lastIndexOf("/") + 1);
            //将ts片段链接加入set集合
            for (int i = 0; i < split.length; i++) {
                String s = split[i];
                if (s.contains("#EXTINF")) {
                    String s1 = split[++i];
                    tsSet.put(StringUtils.isUrl(s1) ? s1 : mergeUrl(relativeUrl, s1), s1);
                }
            }

            System.out.println(" tsSet : " + tsSet.size());
        }


        /**
         * 字段校验
         */
        private void checkField() {
            if ("m3u8".compareTo(MediaFormat.getMediaFormat(DOWNLOADURL)) != 0)
                throw new M3u8Exception(DOWNLOADURL + "不是一个完整m3u8链接！");
            if (threadCount <= 0)
                throw new M3u8Exception("同时下载线程数只能大于0！");
            if (retryCount < 0)
                throw new M3u8Exception("重试次数不能小于0！");
            if (timeoutMillisecond < 0)
                throw new M3u8Exception("超时时间不能小于0！");
            if (StringUtils.isEmpty(dir))
                throw new M3u8Exception("视频存储目录不能为空！");
            if (StringUtils.isEmpty(fileName))
                throw new M3u8Exception("视频名称不能为空！");
            finishedCount = 0;
            tsSet.clear();
            finishedFiles.clear();
            downloadBytes = new BigDecimal(0);
        }

        private String mergeUrl(String start, String end) {
            if (end.startsWith("/"))
                end = end.replaceFirst("/", "");
            int position = 0;
            String subEnd, tempEnd = end;
            while ((position = end.indexOf("/", position)) != -1) {
                subEnd = end.substring(0, position + 1);
                if (start.endsWith(subEnd)) {
                    tempEnd = end.replaceFirst(subEnd, "");
                    break;
                }
                ++position;
            }
            return start + tempEnd;
        }


        private M3u8Download(String DOWNLOADURL) {
            this.DOWNLOADURL = DOWNLOADURL;
        }

    }

    /**
     * 获取实例
     *
     * @param downloadUrl 要下载的链接
     * @return 返回m3u8下载实例
     */
    public static M3u8Download getInstance(String downloadUrl) {
        return new M3u8Download(downloadUrl);
    }

    public static void destroied() {
        m3u8Download = null;
    }

}
