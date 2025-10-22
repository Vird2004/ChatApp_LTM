package client;

import java.awt.Image;

import javax.swing.ImageIcon;

public class Main {

	public static ConnectServerScreen connectServerScreen;
	public static MainScreen mainScreen;
	public static SocketController socketController;

	public static void main(String arg[]) {
		connectServerScreen = new ConnectServerScreen();
	}

//	public static ImageIcon getScaledImage(String path, int width, int height) {
//		Image img = new ImageIcon(Main.class.getResource(path)).getImage();
//		Image scaledImage = img.getScaledInstance(width, height, Image.SCALE_SMOOTH);
//		return new ImageIcon(scaledImage);
//	}
        public static ImageIcon getScaledImage(String path, int width, int height) {
    java.net.URL url = Main.class.getResource(path);
    if (url == null) {
        System.err.println("❌ Không tìm thấy ảnh: " + path);
        return null;
    }

    Image img = new ImageIcon(url).getImage();
    Image scaled = img.getScaledInstance(width, height, Image.SCALE_SMOOTH);
    return new ImageIcon(scaled);
}

}
