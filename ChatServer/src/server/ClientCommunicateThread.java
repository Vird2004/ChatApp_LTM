package server;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ClientCommunicateThread extends Thread {

	Client thisClient;

	public ClientCommunicateThread(Socket clientSocket) {
		try {
			thisClient = new Client();
			thisClient.socket = clientSocket;
			OutputStream os = clientSocket.getOutputStream();
			thisClient.sender = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
			InputStream is = clientSocket.getInputStream();
			thisClient.receiver = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
			thisClient.port = clientSocket.getPort();
		} catch (IOException e) {

		}
	}

	@Override
	public void run() {
		try {
//			while (true) {
//				String header = thisClient.receiver.readLine();
//				if (header == null)
//					throw new IOException();
//
//				System.out.println("Header: " + header);
 while (!thisClient.socket.isClosed()) {
                String header = thisClient.receiver.readLine();
                if (header == null) {
                    System.err.println("⚠️ Mất kết nối với client: " + thisClient.userName);
                    handleDisconnect();
                    continue;
                }
				switch (header) {

				case "new login": {

					String clientUsername = thisClient.receiver.readLine();

					boolean userNameExisted = false;
					for (Client connectedClient : Main.socketController.connectedClient) {
						if (connectedClient.userName.equals(clientUsername)) {
							userNameExisted = true;
							break;
						}
					}

					if (!userNameExisted) {
						thisClient.userName = clientUsername;
						Main.socketController.connectedClient.add(thisClient);
						Main.mainScreen.updateClientTable();

						thisClient.sender.write("login success");
						thisClient.sender.newLine();
						thisClient.sender.flush();

						thisClient.sender.write("" + (Main.socketController.connectedClient.size() - 1));
						thisClient.sender.newLine();
						thisClient.sender.flush();
						for (Client client : Main.socketController.connectedClient) {
							if (client.userName.equals(thisClient.userName))
								continue;
							thisClient.sender.write(client.userName);
							thisClient.sender.newLine();
							thisClient.sender.flush();
						}

						for (Client client : Main.socketController.connectedClient) {
							if (client.userName.equals(thisClient.userName))
								continue;
							client.sender.write("new user online");
							client.sender.newLine();
							client.sender.write(thisClient.userName);
							client.sender.newLine();
							client.sender.flush();
						}
					} else {
						thisClient.sender.write("login failed");
						thisClient.sender.newLine();
						thisClient.sender.flush();
					}
					break;
				}

				case "get name": {
					thisClient.sender.write(Main.socketController.serverName);
					thisClient.sender.newLine();
					thisClient.sender.flush();
					break;
				}

				case "get connected count": {
					thisClient.sender.write("" + Main.socketController.connectedClient.size());
					thisClient.sender.newLine();
					thisClient.sender.flush();
					break;
				}

				case "request create room": {
					String roomName = thisClient.receiver.readLine();
					String roomType = thisClient.receiver.readLine();
					int userCount = Integer.parseInt(thisClient.receiver.readLine());
					List<String> users = new ArrayList<String>();
					for (int i = 0; i < userCount; i++)
						users.add(thisClient.receiver.readLine());

					Room newRoom = new Room(roomName, users);
					Main.socketController.allRooms.add(newRoom);

					for (int i = 0; i < userCount; i++) {
						BufferedWriter currentClientSender = Client.findClient(Main.socketController.connectedClient,
								users.get(i)).sender;
						currentClientSender.write("new room");
						currentClientSender.newLine();
						currentClientSender.write("" + newRoom.id);
						currentClientSender.newLine();
						currentClientSender.write(thisClient.userName);
						currentClientSender.newLine();
						if (roomType.equals("private")) {
							// private chat thì tên room của mỗi người sẽ là tên của người kia
							currentClientSender.write(users.get(1 - i)); // user 0 thì gửi 1, user 1 thì gửi 0
							currentClientSender.newLine();
						} else {
							currentClientSender.write(roomName);
							currentClientSender.newLine();
						}
						currentClientSender.write(roomType);
						currentClientSender.newLine();
						currentClientSender.write("" + users.size());
						currentClientSender.newLine();
						for (String userr : users) {
							currentClientSender.write(userr);
							currentClientSender.newLine();
						}
						currentClientSender.flush();
					}
					break;
				}

				case "text to room": {
					int roomID = Integer.parseInt(thisClient.receiver.readLine());
					String content = "";
					char c;
					do {
						c = (char) thisClient.receiver.read();
						if (c != '\0')
							content += c;
					} while (c != '\0');

					Room room = Room.findRoom(Main.socketController.allRooms, roomID);
					for (String user : room.users) {
						System.out.println("Send text from " + thisClient.userName + " to " + user);
						Client currentClient = Client.findClient(Main.socketController.connectedClient, user);
						if (currentClient != null) {
							currentClient.sender.write("text from user to room");
							currentClient.sender.newLine();
							currentClient.sender.write(thisClient.userName);
							currentClient.sender.newLine();
							currentClient.sender.write("" + roomID);
							currentClient.sender.newLine();
							currentClient.sender.write(content);
							currentClient.sender.write('\0');
							currentClient.sender.flush();
						}
					}
					break;
				}

				case "file to room": {
					int roomID = Integer.parseInt(thisClient.receiver.readLine());
					int roomMessagesCount = Integer.parseInt(thisClient.receiver.readLine());
					String fileName = thisClient.receiver.readLine();
					int fileSize = Integer.parseInt(thisClient.receiver.readLine());

					File filesFolder = new File("files");
					if (!filesFolder.exists())
						filesFolder.mkdir();

					int dotIndex = fileName.lastIndexOf('.');
					String saveFileName = "files/" + fileName.substring(0, dotIndex)
							+ String.format("%02d%03d", roomID, roomMessagesCount) + fileName.substring(dotIndex);

					File file = new File(saveFileName);
					byte[] buffer = new byte[1024];
					InputStream in = thisClient.socket.getInputStream();
					OutputStream out = new FileOutputStream(file);

					int receivedSize = 0;
					int count;
					while ((count = in.read(buffer)) > 0) {
						out.write(buffer, 0, count);
						receivedSize += count;
						if (receivedSize >= fileSize)
							break;
					}

					out.close();

					Room room = Room.findRoom(Main.socketController.allRooms, roomID);
					for (String user : room.users) {
						Client currentClient = Client.findClient(Main.socketController.connectedClient, user);
						if (currentClient != null) {
							currentClient.sender.write("file from user to room");
							currentClient.sender.newLine();
							currentClient.sender.write(thisClient.userName);
							currentClient.sender.newLine();
							currentClient.sender.write("" + roomID);
							currentClient.sender.newLine();
							currentClient.sender.write(fileName);
							currentClient.sender.newLine();
							currentClient.sender.flush();
						}
					}
					break;
				}

				case "audio to room": {
    int roomID = Integer.parseInt(thisClient.receiver.readLine());
    int roomMessagesCount = Integer.parseInt(thisClient.receiver.readLine());
    int audioDuration = Integer.parseInt(thisClient.receiver.readLine());
    int audioByteSize = Integer.parseInt(thisClient.receiver.readLine());

    // 🗂️ Tạo thư mục lưu file nếu chưa có
    File filesFolder = new File("files");
    if (!filesFolder.exists()) filesFolder.mkdir();

    // 🧩 Tạo tên file .wav
    String audioFileName = String.format("/src/files/audio%02d%03d.wav", roomID, roomMessagesCount);
    File file = new File(audioFileName);

    System.out.println("📥 Bắt đầu nhận voice từ " + thisClient.userName +
                       " | Room: " + roomID + " | Dung lượng: " + audioByteSize + " bytes");

    InputStream in = thisClient.socket.getInputStream();
    OutputStream out = new FileOutputStream(file);
    byte[] buffer = new byte[8192];

    int totalRead = 0;
    int bytesRead;
    long start = System.currentTimeMillis();

    // đọc chính xác audioByteSize byte
    while (totalRead < audioByteSize &&
          (bytesRead = in.read(buffer, 0, Math.min(buffer.length, audioByteSize - totalRead))) != -1) {
        out.write(buffer, 0, bytesRead);
        totalRead += bytesRead;
    }

    out.flush();
    //out.close();
    long end = System.currentTimeMillis();

    System.out.println("✅ Đã nhận đủ " + totalRead + "/" + audioByteSize + " bytes");
    System.out.println("⏱️ Thời gian: " + (end - start) + " ms");
    System.out.println("📁 Lưu tại: " + file.getAbsolutePath());

    // 🔁 Gửi thông báo cho các user trong room
    Room room = Room.findRoom(Main.socketController.allRooms, roomID);
    for (String user : room.users) {
        Client currentClient = Client.findClient(Main.socketController.connectedClient, user);
        if (currentClient != null) {
            currentClient.sender.write("audio from user to room");
            currentClient.sender.newLine();
            currentClient.sender.write(thisClient.userName);
            currentClient.sender.newLine();
            currentClient.sender.write(String.valueOf(roomID));
            currentClient.sender.newLine();
            currentClient.sender.write(String.valueOf(audioDuration));
            currentClient.sender.newLine();
            currentClient.sender.flush();
        }
    }

    break;
}


				case "request download file": {
					try {
						int roomID = Integer.parseInt(thisClient.receiver.readLine());
						int messageIndex = Integer.parseInt(thisClient.receiver.readLine());
						String fileName = thisClient.receiver.readLine();

						int dotIndex = fileName.lastIndexOf('.');
						fileName = "files/" + fileName.substring(0, dotIndex)
								+ String.format("%02d%03d", roomID, messageIndex) + fileName.substring(dotIndex);
						File file = new File(fileName);

						thisClient.sender.write("response download file");
						thisClient.sender.newLine();
						thisClient.sender.write("" + file.length());
						thisClient.sender.newLine();
						thisClient.sender.flush();

						byte[] buffer = new byte[1024];
						InputStream in = new FileInputStream(file);
						OutputStream out = thisClient.socket.getOutputStream();

						int count;
						while ((count = in.read(buffer)) > 0) {
							out.write(buffer, 0, count);
						}

						in.close();
						out.flush();
					} catch (IOException ex) {
						ex.printStackTrace();
					}
					break;
				}

				case "request audio bytes": {
    try {
        int roomID = Integer.parseInt(thisClient.receiver.readLine());
        int messageIndex = Integer.parseInt(thisClient.receiver.readLine());

        // ✅ Đặt đúng tên file có đuôi .wav
        String audioFileName = String.format("files/audio%02d%03d.wav", roomID, messageIndex);
        File file = new File(audioFileName);

        // ❌ Nếu file không tồn tại, thông báo lỗi ra console và bỏ qua
        if (!file.exists()) {
            System.err.println("❌ Không tìm thấy file: " + file.getAbsolutePath());
            thisClient.sender.write("response audio notfound");
            thisClient.sender.newLine();
            thisClient.sender.flush();
            break;
        }

        // ✅ Thông báo kích thước file sắp gửi
        thisClient.sender.write("response audio bytes");
        thisClient.sender.newLine();
        thisClient.sender.write(String.valueOf(file.length()));
        thisClient.sender.newLine();
        thisClient.sender.flush();

        // 📤 Gửi file nhị phân
        byte[] buffer = new byte[8192];
        try (InputStream in = new FileInputStream(file);
             OutputStream out = thisClient.socket.getOutputStream()) {

            System.out.println("📤 Đang gửi lại file " + file.getName() +
                               " (" + file.length() + " bytes) cho " + thisClient.userName);

            int bytesRead;
            long totalSent = 0;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalSent += bytesRead;
            }

            out.flush();
            System.out.println("✅ Gửi hoàn tất: " + totalSent + " bytes.");
        }

    } catch (IOException ex) {
        ex.printStackTrace();
    }
    break;
}


				}
			}

		} catch (IOException e) {
			if (!Main.socketController.s.isClosed() && thisClient.userName != null) {

				try {
					for (Client client : Main.socketController.connectedClient) {
						if (!client.userName.equals(thisClient.userName)) {
							client.sender.write("user quit");
							client.sender.newLine();
							client.sender.write(thisClient.userName);
							client.sender.newLine();
							client.sender.flush();
						}
					}

					for (Room room : Main.socketController.allRooms)
						room.users.remove(thisClient.userName);

					thisClient.socket.close();

				} catch (IOException e1) {
					e1.printStackTrace();
				}
				Main.socketController.connectedClient.remove(thisClient);
				Main.mainScreen.updateClientTable();
			}
		}
	}

    private void handleDisconnect() {
    try {
        if (thisClient == null) return;
String name = (thisClient.userName == null) ? "[chưa đăng nhập]" : thisClient.userName;
System.out.println("🔌 Mất kết nối với client: " + name);


        System.out.println("🔌 Ngắt kết nối: " + thisClient.userName);

        // Xóa client khỏi danh sách
        Main.socketController.connectedClient.remove(thisClient);

        // Gửi thông báo tới các client khác
        for (Client c : Main.socketController.connectedClient) {
            if (c.sender == null) continue;
            try {
                c.sender.write("user quit");
                c.sender.newLine();
                c.sender.write(thisClient.userName);
                c.sender.newLine();
                c.sender.flush();
            } catch (Exception ex) {
                System.err.println("⚠️ Không thể gửi user quit tới: " + c.userName);
            }
        }

        for (Room room : Main.socketController.allRooms) {
            room.users.remove(thisClient.userName);
        }

        if (thisClient.socket != null && !thisClient.socket.isClosed()) {
            thisClient.socket.close();
        }

        // Cập nhật giao diện
        if (Main.mainScreen != null)
            Main.mainScreen.updateClientTable();

    } catch (Exception e) {
        System.err.println("❌ Lỗi handleDisconnect: " + e.getMessage());
    }
}

}
