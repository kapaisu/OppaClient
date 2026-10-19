package dev.oppaclient.look;

public final class CapeEditor {
	public static final float ASPECT = 10f / 16f;
	private static final float MAX_ZOOM = 8f;

	private final Images.Image source;
	private final Images.Image picture;
	private float zoom = 1f, cx, cy;
	private Images.Image cape;
	private boolean dirty = true;
	private long changedAt;

	public CapeEditor(Images.Image source) {
		this.source = source;
		int longest = Math.max(source.width(), source.height());
		if (longest > 512) {
			float k = 512f / longest;
			picture = Images.resample(source, 0, 0, source.width(), source.height(), Math.max(1, Math.round(source.width() * k)), Math.max(1, Math.round(source.height() * k)));
		} else {
			picture = source;
		}
		cx = source.width() / 2f;
		cy = source.height() / 2f;
	}

	public Images.Image picture() {
		return picture;
	}

	public float sourceWidth() {
		return source.width();
	}

	public float sourceHeight() {
		return source.height();
	}

	public float cropHeight() {
		return Math.min(source.height(), source.width() / ASPECT) / zoom;
	}

	public float cropWidth() {
		return cropHeight() * ASPECT;
	}

	public float left() {
		return cx - cropWidth() / 2;
	}

	public float top() {
		return cy - cropHeight() / 2;
	}

	public float zoom() {
		return zoom;
	}

	private void clamp() {
		float hw = cropWidth() / 2, hh = cropHeight() / 2;
		cx = Math.max(hw, Math.min(source.width() - hw, cx));
		cy = Math.max(hh, Math.min(source.height() - hh, cy));
		dirty = true;
		changedAt = System.currentTimeMillis();
	}

	public void drag(float dx, float dy, float screenPerPixel) {
		cx -= dx / screenPerPixel;
		cy -= dy / screenPerPixel;
		clamp();
	}

	public void zoom(float value) {
		zoom = Math.max(1f, Math.min(MAX_ZOOM, value));
		clamp();
	}

	private Images.Image compose() {
		return CapeArt.compose(source, Math.round(left()), Math.round(top()), Math.max(1, Math.round(cropWidth())), Math.max(1, Math.round(cropHeight())));
	}

	public Images.Image cape() {
		if (cape == null || dirty && System.currentTimeMillis() - changedAt > 120) {
			cape = compose();
			dirty = false;
		}
		return cape;
	}

	public Images.Image result() {
		return compose();
	}
}
