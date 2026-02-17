package com.homebuying.assistant.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.util.*;

@Service
public class VideoDamageService {

    private final GeminiVisionDamageService gemini;

    public VideoDamageService(GeminiVisionDamageService gemini) {
        this.gemini = gemini;
    }

    public Map<String, Object> checkDamage(MultipartFile file) throws Exception {

        // Save video temporarily
        Path tempVideo = Files.createTempFile("tour-", ".mp4");
        Files.write(tempVideo, file.getBytes());

        // Create temp folder for frames
        Path framesDir = Files.createTempDirectory("frames-");

        List<Path> frames = extractFrames(tempVideo, framesDir);

        if (frames.isEmpty()) {
            return Map.of(
                    "ok", false,
                    "message", "No frames extracted. Install ffmpeg and ensure it runs in terminal: ffmpeg -version"
            );
        }

        // Read images as bytes
        List<byte[]> images = new ArrayList<>();
        for (Path p : frames) {
            images.add(Files.readAllBytes(p));
        }

        // Ask Gemini
        String result = gemini.analyzeDamage(images);

        return Map.of(
                "ok", true,
                "frames", frames.size(),
                "result", result
        );
    }

    private List<Path> extractFrames(Path videoPath, Path outDir) throws Exception {
        // ✅ Full path so it works even if PATH is not available in IntelliJ/Spring
        String ffmpegExe = "C:\\Users\\mrinn\\AppData\\Local\\Microsoft\\WinGet\\Packages\\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\\ffmpeg-8.0.1-full_build\\bin\\ffmpeg.exe";

        String[] times = {"1", "3", "5", "7", "9"};
        List<Path> out = new ArrayList<>();

        for (int i = 0; i < times.length; i++) {
            Path outFile = outDir.resolve("frame-" + (i + 1) + ".jpg");

            List<String> cmd = Arrays.asList(
                    ffmpegExe,                 // ✅ was "ffmpeg"
                    "-y",
                    "-ss", times[i],
                    "-i", videoPath.toAbsolutePath().toString(),
                    "-frames:v", "1",
                    "-q:v", "2",
                    outFile.toAbsolutePath().toString()
            );

            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes());
            int code = p.waitFor();

            if (code != 0) {
                System.out.println("FFMPEG ERROR output:\n" + output);
            }


            if (code == 0 && Files.exists(outFile) && Files.size(outFile) > 0) {
                out.add(outFile);
            }
        }

        return out;
    }

}
