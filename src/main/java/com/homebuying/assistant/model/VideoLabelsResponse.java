package com.homebuying.assistant.model;

import com.homebuying.assistant.dto.VideoChapterDto;
import com.homebuying.assistant.dto.VideoLabelDto;

import java.util.ArrayList;
import java.util.List;

public class VideoLabelsResponse {
    public String fileName;
    public long sizeBytes;
    public List<VideoLabelDto> labels = new ArrayList<>();

    // ✅ NEW: chapters
    public List<VideoChapterDto> chapters = new ArrayList<>();
}