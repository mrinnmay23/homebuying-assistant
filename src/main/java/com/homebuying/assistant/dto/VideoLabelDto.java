package com.homebuying.assistant.dto;

import java.util.ArrayList;
import java.util.List;

public class VideoLabelDto {
    public String label;
    public List<String> categories = new ArrayList<>();
    public List<VideoSegmentDto> segments = new ArrayList<>();

    public VideoLabelDto() {}

    public VideoLabelDto(String label) {
        this.label = label;
    }
}

