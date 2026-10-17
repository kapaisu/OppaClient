package dev.oppaclient.menu;

record Press(double x, double y, int button) {
	boolean secondary() {
		return button == 1;
	}
}
