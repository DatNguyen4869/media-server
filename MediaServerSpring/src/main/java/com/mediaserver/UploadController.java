package com.mediaserver;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.UploadObjectArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class UploadController {

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucketName;

    private static final String UPLOAD_DIR = "/media-server/upload/";
    private static final String HLS_DIR = "/media-server/hls/";
    private static final String CALLBACK_URL = "http://localhost:8081/api/notify"; // Adjust as needed

    private static final Map<String, String> conversionStatus = new ConcurrentHashMap<>();

    @PostMapping("/convert")
    public ResponseEntity<String> handleUpload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty");
        }

        try {
            String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
            String baseName = originalFilename.substring(0, originalFilename.lastIndexOf("."));
            String uniqueName = baseName + "-" + UUID.randomUUID().toString().substring(0, 8);

            Path uploadPath = Paths.get(UPLOAD_DIR, uniqueName + ".mp4");
            Files.createDirectories(uploadPath.getParent());
            file.transferTo(uploadPath);

            Path hlsOutputDir = Paths.get(HLS_DIR, uniqueName);
            Files.createDirectories(hlsOutputDir);

            conversionStatus.put(uniqueName, "processing");

            CompletableFuture.runAsync(() -> {
                String status;
                try {

                    // Check and create bucket if needed
                    boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket("hls-videos").build());
                    if (!exists) {
                        minioClient.makeBucket(MakeBucketArgs.builder().bucket("hls-videos").build());
                    }

                    ProcessBuilder pb = new ProcessBuilder(
                        "ffmpeg",
                        "-i", uploadPath.toString(),
                        "-c:v", "libx264",
                        "-c:a", "aac",
                        "-preset", "veryfast",
                        "-hls_time", "5",
                        "-hls_playlist_type", "vod",
                        "-hls_segment_filename", hlsOutputDir.resolve("segment_%03d.ts").toString(),
                        hlsOutputDir.resolve("index.m3u8").toString()
                    );

                    pb.inheritIO();
                    Process process = pb.start();
                    int exitCode = process.waitFor();

                    if (exitCode == 0) {
                        System.out.println("FFmpeg conversion completed: " + uniqueName);
                        status = "completed";

                        // Upload to MinIO
                        try {

                            Files.walk(hlsOutputDir)
                                    .filter(Files::isRegularFile)
                                    .forEach(path -> {
                                        String objectName = uniqueName + "/" + path.getFileName().toString();
                                        try {
                                            String mimeType = Files.probeContentType(path);
                                            if (mimeType == null) {
                                                if (path.toString().endsWith(".m3u8")) {
                                                    mimeType = "application/vnd.apple.mpegurl";
                                                } else if (path.toString().endsWith(".ts")) {
                                                    mimeType = "video/mp2t";
                                                } else {
                                                    mimeType = "application/octet-stream"; // default fallback
                                                }
                                            }

                                            minioClient.uploadObject(
                                                    UploadObjectArgs.builder()
                                                            .bucket(bucketName)
                                                            .object(objectName)
                                                            .filename(path.toString())
                                                            .contentType(mimeType)
                                                            .build()
                                            );
                                            System.out.println("Uploaded to MinIO: " + objectName);
                                        } catch (Exception e) {
                                            System.err.println("Failed to upload to MinIO: " + objectName);
                                            e.printStackTrace();
                                        }
                                    });
                        } catch (IOException e) {
                            System.err.println("Failed to walk HLS directory: " + hlsOutputDir);
                            e.printStackTrace();
                        }

                    } else {
                        System.err.println("FFmpeg failed with exit code " + exitCode);
                        status = "failed";
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    status = "error";
                }

                conversionStatus.put(uniqueName, status);
                sendCallback(uniqueName, status);
            });

            return ResponseEntity.ok("Conversion started. Job ID: " + uniqueName);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Upload error: " + e.getMessage());
        }
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<String> getStatus(@PathVariable String jobId) {
        String status = conversionStatus.getOrDefault(jobId, "not_found");
        return ResponseEntity.ok("Status for job " + jobId + ": " + status);
    }

    private void sendCallback(String jobId, String status) {
        try {
            URL url = new URL(CALLBACK_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String hlsUrl = "http://localhost:9190/hls/" + jobId + "/index.m3u8";
            String json = String.format("{\"jobId\":\"%s\", \"status\":\"%s\", \"url\":\"%s\"}",
                    jobId, status, hlsUrl);

            byte[] input = json.getBytes(StandardCharsets.UTF_8);
            conn.getOutputStream().write(input);
            int responseCode = conn.getResponseCode();
            System.out.println("Callback response code: " + responseCode);
        } catch (IOException e) {
            System.err.println("Failed to send callback for job " + jobId + ": " + e.getMessage());
        }
    }
}
