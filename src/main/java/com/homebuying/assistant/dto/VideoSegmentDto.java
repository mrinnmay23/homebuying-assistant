package com.homebuying.assistant.dto;

public class VideoSegmentDto {
    public double startSec;
    public double endSec;
    public float confidence;

    public VideoSegmentDto() {}

    public VideoSegmentDto(double startSec, double endSec, float confidence) {
        this.startSec = startSec;
        this.endSec = endSec;
        this.confidence = confidence;
    }
}

