package client;

import java.io.*;
import javax.sound.sampled.*;

public class AudioController {
	
	//Tham khảo: https://stackoverflow.com/a/25813398

	static Thread recordThread;
	static ByteArrayOutputStream out;
	static boolean isRecording = false;
	
	// FIX: Định dạng lý tưởng cho giọng nói (16000.0f, Little-Endian)
	public static AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, false); 

	public static void startRecord() {
		out = new ByteArrayOutputStream();
		isRecording = true;

		recordThread = new Thread(() -> {

			TargetDataLine microphone;
			try {
				microphone = AudioSystem.getTargetDataLine(format);

				DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
				microphone = (TargetDataLine) AudioSystem.getLine(info);
				microphone.open(format);

				int numBytesRead;
				int CHUNK_SIZE = 1024;
				byte[] data = new byte[microphone.getBufferSize() / 5];
				microphone.start();

				int bytesRead = 0;

				try {
					while (isRecording) {
						numBytesRead = microphone.read(data, 0, CHUNK_SIZE);
						bytesRead = bytesRead + numBytesRead;
						out.write(data, 0, numBytesRead);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}

				// Đảm bảo dừng và đóng microphone sạch sẽ
                microphone.stop(); 
				microphone.close();
			} catch (LineUnavailableException e) {
				e.printStackTrace();
			}
		});

		recordThread.start();
	}

// Thay thế phương thức stopRecord trong ChatClient/src/client/AudioController.java

public static byte[] stopRecord() {
    isRecording = false;
    while (recordThread.isAlive()) {
        try {
        Thread.sleep(10);
    } catch (InterruptedException e) {
        break;
    }
    }
    
    byte[] fullAudioBytes = out.toByteArray();
    
    // FIX: Tăng trimSize lên 512 bytes. Đây là mức an toàn hơn 128 bytes
    // và vẫn nhỏ hơn 1/64 giây ghi âm.
    int trimSize = 512; 

    if (fullAudioBytes.length > trimSize) {
        // Tạo một mảng mới với kích thước nhỏ hơn
        byte[] trimmedAudioBytes = new byte[fullAudioBytes.length - trimSize];
        // Sao chép dữ liệu từ đầu đến điểm cắt
        System.arraycopy(fullAudioBytes, 0, trimmedAudioBytes, 0, trimmedAudioBytes.length);
        return trimmedAudioBytes;
    } else {
        // Trường hợp ghi âm quá ngắn
        return fullAudioBytes; 
    }
}
	// FIX 3: CHẠY HÀM PLAY TRONG LUỒNG RIÊNG (ASYNCHRONOUS)
    // Để không chặn luồng Socket (receiveAndProcessThread)
	public static void play(byte[] audioData) {
        new Thread(() -> {
            try {
                // Get an input stream on the byte array
                // containing the data
                InputStream byteArrayInputStream = new ByteArrayInputStream(audioData);
                AudioInputStream audioInputStream = new AudioInputStream(byteArrayInputStream, format, 
                        audioData.length / format.getFrameSize());
                DataLine.Info dataLineInfo = new DataLine.Info(SourceDataLine.class, format);
                SourceDataLine speaker = (SourceDataLine) AudioSystem.getLine(dataLineInfo);
                speaker.open(format);
                speaker.start();
                int cnt = 0;
                byte tempBuffer[] = new byte[10000];
                try {
                    while ((cnt = audioInputStream.read(tempBuffer, 0, tempBuffer.length)) != -1) {
                        if (cnt > 0) {
                            // Write data to the internal buffer of
                            // the data line where it will be
                            // delivered to the speaker.
                            speaker.write(tempBuffer, 0, cnt);
                        } // end if
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // Block and wait for internal buffer of the
                // data line to empty.
                speaker.drain();
                speaker.close();
            } catch (LineUnavailableException ex) {
                ex.printStackTrace();
            }
        }).start(); // Bắt đầu luồng phát nhạc
	}

	public static int getAudioDuration(byte[] audioBytes) {
		AudioInputStream audioInputStream = new AudioInputStream(new ByteArrayInputStream(audioBytes),
				AudioController.format, audioBytes.length);
                return Math.round(audioInputStream.getFrameLength() / audioInputStream.getFormat().getFrameRate());

//return Math.round(audioInputStream.getFrameLength() / audioInputStream.getFormat().getFrameRate() / 2);
	}
        
        // ==================== PHÁT FILE WAV TRỰC TIẾP ====================
public static void play(String filePath) {
    new Thread(() -> {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                System.err.println("❌ File không tồn tại: " + filePath);
                return;
            }

            try (AudioInputStream audioStream = AudioSystem.getAudioInputStream(file)) {
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                SourceDataLine speaker = (SourceDataLine) AudioSystem.getLine(info);
                speaker.open(format);
                speaker.start();

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = audioStream.read(buffer, 0, buffer.length)) != -1) {
                    speaker.write(buffer, 0, bytesRead);
                }

                speaker.drain();
                speaker.close();
                System.out.println("🎧 Phát xong file: " + file.getName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }).start();
}

}