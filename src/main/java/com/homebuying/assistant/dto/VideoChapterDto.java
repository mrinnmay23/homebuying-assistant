package com.homebuying.assistant.dto;

public class VideoChapterDto {
    public double startSec;
    public double endSec;

    public VideoChapterDto() {}

    public VideoChapterDto(double startSec, double endSec) {
        this.startSec = startSec;
        this.endSec = endSec;
    }
}
