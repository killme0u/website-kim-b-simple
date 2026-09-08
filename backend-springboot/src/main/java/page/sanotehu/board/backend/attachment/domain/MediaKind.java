package page.sanotehu.board.backend.attachment.domain;

public enum MediaKind {
    IMAGE, VIDEO, AUDIO, FILE;

    public static MediaKind from(String contentType) {
        if ("image/svg+xml".equals(contentType)) return FILE; // SVG는 스크립트 실행 가능
        if (contentType.startsWith("image/")) return IMAGE;
        if (contentType.startsWith("video/")) return VIDEO;
        if (contentType.startsWith("audio/")) return AUDIO;
        return FILE;
    }
}