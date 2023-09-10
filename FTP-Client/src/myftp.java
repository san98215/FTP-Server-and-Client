import java.net.*;
import java.util.HashMap;
import java.io.*;

public class myftp {

	// Create socket & streams
	private Socket socket = null;
	private Socket tsocket = null;
	private Socket threadSocket = null;
	private BufferedReader input = null;
	private DataOutputStream out = null;
	private DataInputStream in = null;
    private DataOutputStream out1 = null;
    private DataInputStream in1 = null;
	private boolean newThread = false; // flag to spawn a new thread
	private String path;
	private static HashMap<Integer, ClientThread> threadList = new HashMap<Integer, ClientThread>(); // stores spawned
																										// threads for
																										// possible
																										// termination

	// Constructor using ip address and port
	public myftp(String name, int nport, int tport) {
		// Establish connection
		try {
			socket = new Socket(name, nport);
			System.out.println("Connected");

			// Take input from terminal
			input = new BufferedReader(new InputStreamReader(System.in));

			// Sends output to server
			out = new DataOutputStream(socket.getOutputStream());

			// Takes input from server
			in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
		} catch (UnknownHostException uhe) {
			System.out.println(uhe);
		} catch (IOException ioe) {
			System.out.println(ioe);
		}

		// Create empty strings to fill for input
		String line = "";
		String msg = "";
		String[] msg1 = null;

		// Read commands until "quit" is sent
		while (!line.equals("quit")) {
			try {
				System.out.print("mytftp> ");
				line = input.readLine();

				// Handle commands to spawn new thread
				if (line.endsWith("&")) {
					newThread = true;
					msg1 = line.split(" ");

					if (msg1[0].equals("get")) {
						if (msg1[1].endsWith("&"))
							msg1[1] = msg1[1].substring(0, msg1[1].length() - 1); // Store argument of command excluding
																					// '&'
					} else if (msg1[0].equals("put")) {
						if (msg1[1].endsWith("&"))
							msg1[1] = msg1[1].substring(0, msg1[1].length() - 1);
					}
				}
				synchronized (this) {
					out.writeUTF(line);
				}

				// Prepares client to spawn thread by creating a new socket to the server to
				// handle the command of the thread separately
				if (newThread) {
					path = in.readUTF();
					threadSocket = new Socket(name, nport);

					out1 = new DataOutputStream(threadSocket.getOutputStream());

					in1 = new DataInputStream(new BufferedInputStream(threadSocket.getInputStream()));
				}

				// Receive base command from server
				if (!newThread) msg = in.readUTF();
				else msg = in1.readUTF();

				if (!msg.equals("quit")) {

					// Handles terminate commands
					if (msg.equals("terminate")) {
						int cmdID = in.readInt();

						// Handle wrong command for terminate
						if (threadList.get(cmdID) == null) {
							System.out.println("Command " + cmdID + " is not in operation.\n");
						} else {
							threadList.get(cmdID).setTerm(true);

							System.out.println("Command " + cmdID + " is terminating.\n");
						}

					}
					// Handles normal get command
					else if (!newThread && msg.equals("get")) {
						int cmdID = in.readInt();
						System.out.println("Command ID: " + cmdID);
						String fileName1 = in.readUTF();
						boolean fileExists = in.readBoolean();

						if (fileExists) {
							int size = in.readInt();
							byte[] bytes = new byte[size];
							FileOutputStream fos = new FileOutputStream(fileName1);
							BufferedOutputStream bos = new BufferedOutputStream(fos);
							int offset = 0;
							int end = 0;

							// Reads and writes 1000 bytes at a time
							while (offset < size) {
								end = ((offset + 1000) < size) ? 1000 : size - offset;
								in.read(bytes, offset, end);
								bos.write(bytes, offset, end);
								offset += 1000;
							}
							bos.close();

							System.out.println("File " + fileName1 + " has been downloaded.\n");
						} else
							System.out.println("Error: File does not exist.\n");
					}

					// Handles normal put command
					else if (!newThread && msg.equals("put")) {
						int cmdID = in.readInt();
						System.out.println("Command ID: " + cmdID);
						String fileName = in.readUTF();
						File file = new File(fileName);

						if (file.exists()) {
							synchronized (this) {
								out.writeBoolean(true);
							}
							byte[] bytes = new byte[(int) file.length()];
							int size = bytes.length;
							BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
							synchronized (this) {
								out.writeInt(bytes.length);
							}
							int offset = 0;
							int end = 0;

							while (offset < size) {
								end = ((offset + 1000) < size) ? 1000 : size - offset;
								bis.read(bytes, offset, end);
								synchronized (this) {
									out.write(bytes, offset, end);
								}
								offset += 1000;
							}
							bis.close();

							// Prints message of success
							System.out.println(in.readUTF());
						} else {
							synchronized (this) {
								out.writeBoolean(false);
							}

							// Prints message of non-existent file
							System.out.println(in.readUTF());
						}
					}

					// Handles commands that spawn new threads
					else if (newThread && (msg.equals("get") || msg.equals("put"))) {
						int cmdID = in1.readInt();
						System.out.println("Command ID: " + cmdID);

						// Checks for existing file here to keep proper syntax
						if (msg.equals("get")) {
							if (!new File(path + "/" + msg1[1]).exists())
								System.out.println("Error: File does not exist.");
							else
								System.out.println("Your File is being downloaded.");
						}	
						else {
							if (!new File(msg1[1]).exists())
								System.out.println("Error: File does not exist.");
							else
								System.out.println("Your File is being sent");
						}

						// Spawn new thread
						synchronized (this) {
							System.out.println();
							ClientThread task = new ClientThread(threadSocket, msg, name, tport, cmdID);
							threadList.put(cmdID, task); // Store thread and command ID in case the command needs to be
															// terminated
							task.start();
						}
						newThread = false;

					}

					// Handles all other commands
					else if (!msg.equals("get") && !msg.equals("put")) {
						System.out.println(msg);
					}
				}

				else
					line = "quit";
			} catch (IOException i) {
				System.out.println(i + "\nServer forcibly closed.");
				line = "quit";
			}
		}

		// Closes the connection
		try {
			System.out.println("Closing connection");
			input.close();
			out.close();
			socket.close();
		} catch (IOException ioe) {
			System.out.println(ioe);
		}
	}

	/*
	 * Creates connection to termination port on server and passes the command ID of
	 * the command to be terminated
	 */
	public void terminate(String name, int tport, int cmdID) {
		try {
			tsocket = new Socket(name, tport);

			// Set up output connection to termination socket
			DataOutputStream output = new DataOutputStream(tsocket.getOutputStream());

			output.writeInt(cmdID);
			output.close();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private class ClientThread extends Thread {
		// Create sockets, streams, and other necessary variables
		private Socket socket = null;
		private String name;
		private String line; // Stores initial command from user that spawned thread
		private boolean terminate; // Flag for terminating commands
		private int tport;
		private int cmdID;

		// Constructor for ClientThread
		public ClientThread(Socket socket, String line, String name, int tport, int cmdID) {
			this.socket = socket;
			this.line = line;
			this.name = name;
			this.tport = tport;
			this.cmdID = cmdID;
		}

		/*
		 * Handles thread-based commands. The functionality of the commands does not
		 * change, and are implemented similarly to the normal commands.
		 */
		public void run() {
		    
		    try {

			    if (line.equals("get")) {
					String fileName1 = in1.readUTF();
					boolean fileExists = in1.readBoolean();

					if (fileExists) {
						int size = in1.readInt();
						byte[] bytes = new byte[size];
						FileOutputStream fos = new FileOutputStream(fileName1);
						BufferedOutputStream bos = new BufferedOutputStream(fos);
						int offset = 0;
						int end = 0;

						while (offset < size) {
							if (!terminate) {
								end = ((offset + 1000) < size) ? 1000 : size - offset;
								in1.read(bytes, offset, end);
								bos.write(bytes, offset, end);
								offset += 1000;
							} else {
								terminate(name, tport, cmdID);
								bytes = null;
								break;
							}

						}
						bos.close();

						if (bytes == null) {
							File file = new File(System.getProperty("user.dir") + "/" + fileName1);
							file.delete();
						}
					}
				} else if (line.equals("put")) {
					String fileName = in1.readUTF();
					File file = new File(fileName);

					if (file.exists()) {
						synchronized (this) {
							out1.writeBoolean(true);
						}
						byte[] bytes = new byte[(int) file.length()];
						int size = bytes.length;
						BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
						synchronized (this) {
							out1.writeInt(bytes.length);
						}
						int offset = 0;
						int end = 0;
						while (offset < size) {
							if (!terminate) {
								end = ((offset + 1000) < size) ? 1000 : size - offset;
								bis.read(bytes, offset, end);
								synchronized (this) {
									out1.write(bytes, offset, end);
								}
								offset += 1000;
							} else {
								terminate(name, tport, cmdID);
								bytes = null;
								break;
							}

						}
						bis.close();
					} else {
						synchronized (this) {
							out1.writeBoolean(false);
						}
					}
				}
				System.out.print("\nFile transfer complete. \n\nmytftp> ");
				out1.close();
				in1.close();
				socket.close();
				threadSocket = null;

				interrupt();
			} catch (IOException i) {
			i.printStackTrace();
				System.out.println(i);
			}
		}

		// Sets the flag for a command to terminate
		public void setTerm(boolean term) {
			terminate = term;
		}
	}

	/*
	 * Creates an instance of myftp using command line arguments machineName handles
	 * the address of the server to connect to nport stores the port number of the
	 * normal port on the server tport stores the port number of the termination
	 * port on the server
	 */
	public static void main(String args[]) {
		String machineName = args[0];
		int nport = Integer.parseInt(args[1]);
		int tport = Integer.parseInt(args[2]);

		myftp client = new myftp(machineName, nport, tport);
	}

}
