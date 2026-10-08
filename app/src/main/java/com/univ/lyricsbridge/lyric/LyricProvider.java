package com.univ.lyricsbridge.lyric;

import com.univ.lyricsbridge.model.TrackInfo;
import java.io.IOException;
import java.util.List;

public interface LyricProvider {
    List<LyricCandidate> search(TrackInfo track) throws IOException;
}
