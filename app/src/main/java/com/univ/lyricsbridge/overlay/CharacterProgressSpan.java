package com.univ.lyricsbridge.overlay;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

/** Draws a single character with a left-to-right timed color sweep. */
final class CharacterProgressSpan extends ReplacementSpan {
    private final String character;
    private final int sungColor;
    private final int unsungColor;
    private final float progress;

    CharacterProgressSpan(String character, int sungColor, int unsungColor, float progress) {
        this.character = character;
        this.sungColor = sungColor;
        this.unsungColor = unsungColor;
        this.progress = Math.max(0f, Math.min(1f, progress));
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
        if (metrics != null) {
            Paint.FontMetricsInt source = paint.getFontMetricsInt();
            metrics.top = source.top;
            metrics.ascent = source.ascent;
            metrics.descent = source.descent;
            metrics.bottom = source.bottom;
            metrics.leading = source.leading;
        }
        return Math.round(paint.measureText(character));
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x,
                     int top, int y, int bottom, Paint paint) {
        int oldColor = paint.getColor();
        float width = paint.measureText(character);
        paint.setColor(unsungColor);
        canvas.drawText(character, x, y, paint);
        if (progress > 0f) {
            int save = canvas.save();
            canvas.clipRect(x, top, x + width * progress, bottom);
            paint.setColor(sungColor);
            canvas.drawText(character, x, y, paint);
            canvas.restoreToCount(save);
        }
        paint.setColor(oldColor);
    }
}
