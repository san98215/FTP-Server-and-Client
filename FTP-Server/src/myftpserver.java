import java.net.*;
import java.io.*;
import java.util.HashMap;

public class myftpserver {

	// initializes class variables
	private String[] threadedLine = null; // Stores command to be threaded on the client
	private boolean threadCon = false; // Flag to indicate if a command to be threaded on the client has been received
	private static int cmdNum = 0;
	private final String serverPath = new File(System.getProperty("user.dir")).getAbsolutePath();
	private String remotePath;
	private static HashMap<Integer, Boolean> cmdID = new HashMap<Integer, Boolean>(); // Stores command IDs and sets a
																						// termination flag for each
	private static HashMap<String, String> fileStore = new HashMap<String, String>(); // Stores files and the commands
																						// using those files currently

	/*
	 * Constructor for Server nport - port for client thread tport - port for
	 * terminate thread
	 */
	public myftpserver(int nport, int tport) {
		Thread normalPort = new ServerThread(nport, true);
		normalPort.start();

		Thread termPort = new ServerThread(tport, false);
		termPort.start();
	}

	/*
	 * Server Thread, master thread for spawning future client threads
	 */
	private class ServerThread extends Thread {
		private Socket clientSocket = null;
		private int port;
		private boolean norm; // Flag to indicate whether the thread should accept clients or terminate
								// commands

		public ServerThread(int port, boolean norm) {
			this.port = port;
			this.norm = norm;
		}

		public void run() {
			ServerSocket server;

			try {
				// Create server socket to listen for incoming connect requests
				server = new ServerSocket(port);
				server.setReuseAddress(true);

				//
				if (norm) {
					String[] cmd = { "" };
					System.out.println("Locating a client...");

					// Wait for client connections continuously
					while (true) {
						clientSocket = server.accept();
						synchronized (this) {
							// If client's first connection
							if (!threadCon) {
								System.out.println("Client found");
								Thread clientThread = new ClientThread(clientSocket, cmd, false);
								clientThread.start();
							}
							// If client spawned a new thread, create a new server thread to connect to the
							// client thread
							else {
								Thread clientThread = new ClientThread(clientSocket, threadedLine, true);
								clientThread.start();
								threadCon = false;
							}
						}
					}
				}
				// Termination thread
				else {
					System.out.println("Waiting for terminate command...");

					while (true) {
						clientSocket = server.accept();
						System.out.println("Terminate command received");
						Thread termThread = new TerminatorThread(clientSocket, port);
						termThread.start();
					}
				}

			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/*
	 * Class object to use for Client Threads. Simply create a Thread object with
	 * new ClientThread - call this constructor then use ".start()" to invoke a new
	 * thread
	 */
	private class ClientThread extends Thread {
		private Socket clientSocket;
		private DataInputStream input = null;
		private DataOutputStream out = null;
		private String[] cmd = { "" }; // Stores current command received from client
		private String path = null; // Stores current file path of the client
		private boolean newThread = false; // Flag to indicate a client spawned a new thread

		// Constructor for ClientThread
		public ClientThread(Socket socket, String[] cmd, boolean newThread) {
			this.clientSocket = socket;
			this.newThread = newThread;
			this.cmd = cmd;
		}

		public void run() {
			try {
				while (true) {
					input = new DataInputStream(new BufferedInputStream(clientSocket.getInputStream()));

					out = new DataOutputStream(clientSocket.getOutputStream());

					if (!newThread)
						path = serverPath;
					else
						path = remotePath;

					String[] line = cmd;

					try {
						// Reads from client until quit command is received
						while (!line[0].equals("quit")) {
							synchronized (this) {

								// Gets input from client if this is not a thread connected to a client thread
								if (!newThread)
									line = input.readUTF().split(" ");

								// Handles commands for a new client thread
								if (line.length > 2 && line[2].equals("&")) {
									threadCon = true; // Sets flag to accept a new connection to the thread spawned by
														// the client
									out.writeUTF(path);
									line = new String[] { line[0], line[1] };
									threadedLine = line; // Stores current command received from client for new thread
									remotePath = path;
								} else if (line.length > 1 && line[1].endsWith("&")) {
									threadCon = true;
									out.writeUTF(path);
									line[1] = line[1].substring(0, line[1].length() - 1);
									threadedLine = line;
									remotePath = path;
								} else
									read(line); // Handles all commands

								// Exits loop to terminate a new thread handling a command with '&'
								if (newThread)
									break;

							}
						}
					} catch (IOException ioe) {
						ioe.printStackTrace();
					}

					// Closes connection with an actual client
					if (!newThread) {
						System.out.println("Closing connection");

						// close client connection
						input.close();
						out.close();
						clientSocket.close();
					} else
						interrupt(); // Kill thread that finished handling command with '&'
					break;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		/*
		 * Master function for all commands
		 */
		public synchronized void read(String[] line) {
			try {
				if (line[0].equals("ls")) {
					ls();
				} else if (line[0].equals("cd")) {
					cd(line[1]);
				} else if (line[0].equals("mkdir")) {
					mkdir(line[1]);
				} else if (line[0].equals("get")) {
					int curCmd = cmdNum;

					try {
						synchronized (this) {
							out.writeUTF("get");
							curCmd = cmdNum;
							out.writeInt(cmdNum); // Sends base command to client
							cmdID.put(cmdNum, false); // Stores command ID for the new command
							cmdNum++;
							out.writeUTF(line[1]); // Sends argument for command to client
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
					get(line[1], curCmd);
				} else if (line[0].equals("put")) {
					int curCmd = cmdNum;

					try {
						synchronized (this) {
							out.writeUTF("put");
							curCmd = cmdNum;
							out.writeInt(cmdNum); // Sends base command to client
							cmdID.put(cmdNum, false); // Stores command ID for the new command
							cmdNum++;
							out.writeUTF(line[1]); // Sends argument for command to client
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
					put(line[1], curCmd);
				} else if (line[0].equals("pwd")) {
					pwd();
				} else if (line[0].equals("delete")) {
					delete(line[1]);
				} else if (line[0].equals("terminate")) {
					try {
						synchronized (this) {
							out.writeUTF(line[0]); // Sends terminate command back to client
							out.writeInt(Integer.parseInt(line[1])); // Sends command ID back to client
						}

					} catch (IOException e) {
						e.printStackTrace();
					}
				} else if (line[0].equals("quit")) {
					try {
						synchronized (this) {
							out.writeUTF(line[0]);
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					try {
						synchronized (this) {
							out.writeUTF("Please enter a valid command!\n");
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			} catch (ArrayIndexOutOfBoundsException c) {
				try {
					synchronized (this) {
						out.writeUTF("Error: Missing Field\n");
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		// ls command will display all files in current directory
		public void ls() {
			String message = "";
			File file = new File(path);
			String listFiles[] = file.list();
			for (String item : listFiles) {
				message += (item + "\t");
			}
			try {
				synchronized (this) {
					out.writeUTF(message + "\n");
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		// cd command will change the working directory to another
		public void cd(String dir) {
			File start = new File(path);

			// Handle .. and ../
			if (dir.contains("..")) {
				path = start.getParent();
				if (dir.equals("..")) {
					dir = "";
				} else {
					dir = dir.substring(dir.indexOf('/') + 1, dir.length());
				}
			}

			File file = new File(path + "/" + dir);

			if (file.exists()) {
				path = file.getAbsolutePath();
				try {
					synchronized (this) {
						out.writeUTF("Your working directory is now: " + path + "\n");
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else {

				try {
					synchronized (this) {
						out.writeUTF("This directory does not exist, please enter valid directory!\n");
					}
				} catch (IOException e) {
					e.printStackTrace();
				}

			}
		}

		// mkdir command creates a file in the current directory
		public void mkdir(String line) {
			File dir = new File(path + "/" + line);
			String fileInUse = dir.getAbsolutePath();
			boolean sameFile = false;

			synchronized (this) {
				// Prevents calls to make a directory that's currently be deleted
				while (sameFile) {
					sameFile = (fileStore.get(fileInUse) != null) && ((fileStore.get(fileInUse).equals("delete")));
				}
			}

			try {
				if (!dir.exists()) {
					dir.mkdirs();
					synchronized (this) {
						out.writeUTF("Made " + line + "\n");
					}
				} else {
					synchronized (this) {
						out.writeUTF(line + " already exists.\n");
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		/*
		 * Retrieves a file from a remote directory and places it in the current
		 * directory
		 */
		public synchronized void get(String line, int cmd) {
			try {
				File file = new File(path + "/" + line);
				String fileInUse = file.getAbsolutePath();
				boolean sameFile = false;

				synchronized (this) {
					// Prevents call to get a file that's currently being deleted
					String test = fileStore.get(fileInUse);
					sameFile = (test != null && (test.equals("delete"))) || (test != null && test.equals("put"))
							|| (test != null && test.equals("get"));
					if (sameFile)
						System.out.println("Waiting for previous command on file to finish.");
					while (sameFile) {
						test = fileStore.get(fileInUse);
						sameFile = (test != null && (test.equals("delete"))) || (test != null && test.equals("put"))
								|| (test != null && test.equals("get"));
					}
					fileStore.put(fileInUse, "get");
				}

				if (file.exists()) {
					synchronized (this) {
						out.writeBoolean(true);
					}
					byte[] bytes = new byte[(int) file.length()];
					synchronized (this) {
						out.writeInt(bytes.length);
					}
					BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
					int offset = 0;
					int end = 0;
					
					while (offset < bytes.length) {
						// While the terminate flag for this command is false read/write 1000 bytes at a
						// time
						if (!cmdID.get(cmd)) {
							end = ((offset + 1000) < bytes.length) ? 1000 : bytes.length - offset;
							bis.read(bytes, offset, end);
							synchronized (this) {
								out.write(bytes, offset, end);
							}
							offset += 1000;
						}
						// Terminate command received and flag set to true
						else {
							bytes = null;
							break;
						}
					}
					bis.close();
				} else {
					synchronized (this) {
						out.writeBoolean(false);
					}
				}
				synchronized (this) {
					fileStore.remove(fileInUse);
				}

			} catch (IOException ioe) {
				try {
					synchronized (this) {
						out.writeUTF("Error: No such File\n");
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		/*
		 * Copies a file from the current directory and places it in the current remote
		 * directory
		 */
		public synchronized void put(String src, int cmd) {
			try {
				String fileInUse = new File(path + "/" + src).getAbsolutePath();
				boolean sameFile = true;

				synchronized (this) {
					String test = fileStore.get(fileInUse);
					sameFile = (test != null && (test.equals("delete"))) || (test != null && test.equals("put"))
							|| (test != null && test.equals("get"));
					if (sameFile)
						System.out.println("Waiting for previous command on file to finish.");
					while (sameFile) {
						test = fileStore.get(fileInUse);
						sameFile = (test != null && (test.equals("delete"))) || (test != null && test.equals("put"))
								|| (test != null && test.equals("get"));
					}
					fileStore.put(fileInUse, "put");
				}

				if (!sameFile) {
					String fileName = path + "/" + src;
					boolean fileExists = input.readBoolean();

					if (fileExists) {
						int size = input.readInt();
						byte[] bytes = new byte[size];
						FileOutputStream fos = new FileOutputStream(fileName);
						BufferedOutputStream bos = new BufferedOutputStream(fos);
						int offset = 0;
						int end = 0;

						while (offset < size) {
							if (!cmdID.get(cmd)) {
								end = ((offset + 1000) < size) ? 1000 : size - offset;
								input.read(bytes, offset, end);
								bos.write(bytes, offset, end);
								offset += 1000;
							} else {
								bytes = null;
								break;
							}

						}
						bos.close();
						if (bytes == null) {
							File file = new File(path + "/" + src);
							file.delete();
						} else {
							synchronized (this) {
								out.writeUTF("File " + src + " has been sent.\n");
							}
						}
					} else
						out.writeUTF("Error: File does not exist.\n");
				}

				synchronized (this) {
					fileStore.remove(fileInUse);
				}

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		// Outputs present working directory
		public void pwd() {
			try {
				synchronized (this) {
					out.writeUTF(path + "\n");
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		// Deletes a specified local file
		public synchronized void delete(String line) {
			boolean sameFile = true;
			File dir = new File(path + "/" + line);
			String fileInUse = dir.getAbsolutePath();

			synchronized (this) {
				String test = fileStore.get(fileInUse);
				sameFile = (test != null && (test.equals("delete"))) || (test != null && test.equals("put"))
						|| (test != null && test.equals("get"));
				if (sameFile)
					System.out.println("Waiting for previous command on file to finish.");
				while (sameFile) {
					test = fileStore.get(fileInUse);
					sameFile = (test != null && (test.equals("delete"))) || (test != null && test.equals("put"))
							|| (test != null && test.equals("get"));
				}
				fileStore.put(fileInUse, "delete");
			}

			if (dir.exists()) {
				File[] dirContents = dir.listFiles();

				try {
					if (dirContents != null) {
						for (File file : dirContents) {
							delete(file.toString());
						}
					}
					synchronized (this) {
						out.writeUTF("Deleted " + line + ".\n");
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				dir.delete();
			} else {
				try {
					synchronized (this) {
						out.writeUTF("Error: File does not exist\n");
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			synchronized (this) {
				fileStore.remove(fileInUse);
			}

		}
	}

	/*
	 * Terminator Class Skeleton code for now, we need to conceptualize this first
	 * [delete this]
	 */
	private class TerminatorThread extends Thread {
		private Socket terminatorSocket;
		private DataInputStream input = null;

		public TerminatorThread(Socket socket, int tport) {
			this.terminatorSocket = socket;
		}

		// Run is used by Runnable which was inherited by Thread and must be overwritten
		public void run() {
			try {
				input = new DataInputStream(new BufferedInputStream(terminatorSocket.getInputStream()));

				synchronized (this) {
					int cmd = input.readInt();
					cmdID.replace(cmd, true);
				}

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	// main
	public static void main(String[] args) {
		int nport = Integer.parseInt(args[0]);
		int tport = Integer.parseInt(args[1]);
		myftpserver server = new myftpserver(nport, tport);
	}

}
