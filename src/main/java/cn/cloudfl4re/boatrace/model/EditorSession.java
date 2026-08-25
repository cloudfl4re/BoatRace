package cn.cloudfl4re.boatrace.model;

public record EditorSession(TrackDraft draft, PointSelection selection, boolean preview) {
    public static EditorSession create(TrackDraft draft) {
        return new EditorSession(draft, new PointSelection(null, null, null), true);
    }

    public EditorSession withDraft(TrackDraft value) {
        return new EditorSession(value, selection, preview);
    }

    public EditorSession withSelection(PointSelection value) {
        return new EditorSession(draft, value, preview);
    }

    public EditorSession withPreview(boolean value) {
        return new EditorSession(draft, selection, value);
    }
}
