package com.zetcode;

import com.fazecast.jSerialComm.SerialPort;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class Board extends JPanel implements ActionListener {

    private final int B_WIDTH = 300;
    private final int B_HEIGHT = 300;
    private final int DOT_SIZE = 10;
    private final int ALL_DOTS = 900;
    private final int RAND_POS = 29;
    private final int DELAY = 140;

    private final int x[] = new int[ALL_DOTS];
    private final int y[] = new int[ALL_DOTS];

    private int dots;
    private int apple_x;
    private int apple_y;

    private boolean leftDirection = false;
    private boolean rightDirection = true;
    private boolean upDirection = false;
    private boolean downDirection = false;
    private volatile boolean inGame = false; // Starts false until green button pressed
    private volatile boolean gameStartedOnce = false;
    private boolean turnedThisTick = false; // allow only one turn per game tick

    private Timer timer;
    private Image ball;
    private Image apple;
    private Image head;
    
    private static final String PORT_NAME = "COM7"; // COM7 = virtual port, change to real Arduino port later
    private static SerialPort serialPort;

    public Board() {
        initBoard();
        setupSerialPort();
    }
    
    private void setupSerialPort() {
        serialPort = SerialPort.getCommPort(PORT_NAME);
        serialPort.setBaudRate(9600);
        serialPort.setNumDataBits(8);
        serialPort.setNumStopBits(SerialPort.ONE_STOP_BIT);
        serialPort.setParity(SerialPort.NO_PARITY);

        if (serialPort.openPort()) {
            System.out.println("SUCCESS: Connected to port " + PORT_NAME + " (Board v3 polling)");

            Thread serialListener = new Thread(() -> {
                System.out.println("Serial listener thread started.");
                StringBuilder sb = new StringBuilder();
                while (serialPort.isOpen()) {
                    int available = serialPort.bytesAvailable();
                    if (available > 0) {
                        byte[] buf = new byte[available];
                        int n = serialPort.readBytes(buf, buf.length);
                        for (int i = 0; i < n; i++) {
                            char c = (char) buf[i];
                            if (c == '\n' || c == '\r') {
                                if (sb.length() > 0) {
                                    final String line = sb.toString().trim();
                                    sb.setLength(0);
                                    System.out.println("RECEIVED: " + line);
                                    // Run game logic on the Swing thread, not the serial thread
                                    SwingUtilities.invokeLater(() -> handleCommand(line));
                                }
                            } else {
                                sb.append(c);
                            }
                        }
                    } else if (available < 0) {
                        break; // port error / closed
                    } else {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException ex) {
                            break;
                        }
                    }
                }
                System.out.println("Serial listener stopped.");
            });
            serialListener.setDaemon(true);
            serialListener.start();
        } else {
            System.out.println("ERROR: Could not open " + PORT_NAME + ". Check connection.");
        }
    }

    private void handleCommand(String line) {
        if (line.equals("L")) {
            turnCounterClockwise();
        } else if (line.equals("R")) {
            turnClockwise();
        } else if (line.equals("START")) {
            if (!inGame) {
                initGame();
            }
        }
    }

    private void initBoard() {
        addKeyListener(new TAdapter());
        setBackground(Color.black);
        setFocusable(true);

        setPreferredSize(new Dimension(B_WIDTH, B_HEIGHT));
        loadImages();
        
        timer = new Timer(DELAY, this);
    }

    private void loadImages() {
        ImageIcon iid = new ImageIcon("src/resources/dot.png");
        ball = iid.getImage();

        ImageIcon iia = new ImageIcon("src/resources/apple.png");
        apple = iia.getImage();

        ImageIcon iih = new ImageIcon("src/resources/head.png");
        head = iih.getImage();
    }

    private void initGame() {
        dots = 3;
        leftDirection = false;
        rightDirection = true;
        upDirection = false;
        downDirection = false;
        turnedThisTick = false;

        for (int z = 0; z < dots; z++) {
            x[z] = 50 - z * 10;
            y[z] = 50;
        }
        
        locateApple();

        inGame = true;
        gameStartedOnce = true;
        timer.start();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        doDrawing(g);
    }
    
    private void doDrawing(Graphics g) {
        if (!gameStartedOnce) {
            String msg = "PRESS GREEN BUTTON";
            String subMsg = "TO START GAME";
            Font small = new Font("Helvetica", Font.BOLD, 14);
            FontMetrics metr = getFontMetrics(small);

            g.setColor(Color.white);
            g.setFont(small);
            g.drawString(msg, (B_WIDTH - metr.stringWidth(msg)) / 2, B_HEIGHT / 2 - 10);
            g.drawString(subMsg, (B_WIDTH - metr.stringWidth(subMsg)) / 2, B_HEIGHT / 2 + 15);
            return;
        }

        if (inGame) {
            g.drawImage(apple, apple_x, apple_y, this);

            for (int z = 0; z < dots; z++) {
                if (z == 0) {
                    g.drawImage(head, x[z], y[z], this);
                } else {
                    g.drawImage(ball, x[z], y[z], this);
                }
            }

            Toolkit.getDefaultToolkit().sync();

        } else {
            gameOver(g);
        }         
    }

    private void gameOver(Graphics g) {
        String msg = "Game Over";
        Font small = new Font("Helvetica", Font.BOLD, 14);
        FontMetrics metr = getFontMetrics(small);

        g.setColor(Color.white);
        g.setFont(small);
        g.drawString(msg, (B_WIDTH - metr.stringWidth(msg)) / 2, B_HEIGHT / 2);
    }

    private void checkApple() {
        if ((x[0] == apple_x) && (y[0] == apple_y)) {
            dots++;
            locateApple();
        }
    }

    private void move() {
        for (int z = dots; z > 0; z--) {
            x[z] = x[(z - 1)];
            y[z] = y[(z - 1)];
        }

        if (leftDirection) {
            x[0] -= DOT_SIZE;
        }

        if (rightDirection) {
            x[0] += DOT_SIZE;
        }

        if (upDirection) {
            y[0] -= DOT_SIZE;
        }

        if (downDirection) {
            y[0] += DOT_SIZE;
        }
    }

    private void checkCollision() {
        for (int z = dots; z > 0; z--) {
            if ((z > 4) && (x[0] == x[z]) && (y[0] == y[z])) {
                inGame = false;
            }
        }

        if (y[0] >= B_HEIGHT || y[0] < 0 || x[0] >= B_WIDTH || x[0] < 0) {
            inGame = false;
        }
        
        if (!inGame) {
            timer.stop();
            // Send game over signal 'G' to Arduino for Red LED & Buzzer sound
            if (serialPort != null && serialPort.isOpen()) {
                byte[] gameOverSignal = {'G'};
                serialPort.writeBytes(gameOverSignal, 1);
            }
        }
    }

    private void locateApple() {
        int r = (int) (Math.random() * RAND_POS);
        apple_x = ((r * DOT_SIZE));

        r = (int) (Math.random() * RAND_POS);
        apple_y = ((r * DOT_SIZE));
    }

    // Relative steering for the 2 Arduino buttons (L = counter-clockwise, R = clockwise)
    public void turnCounterClockwise() {
        if (!inGame || turnedThisTick) return;
        if (rightDirection) {        // right -> up
            setHeading(false, false, true, false);
        } else if (upDirection) {    // up -> left
            setHeading(true, false, false, false);
        } else if (leftDirection) {  // left -> down
            setHeading(false, false, false, true);
        } else {                     // down -> right
            setHeading(false, true, false, false);
        }
        turnedThisTick = true;
    }

    public void turnClockwise() {
        if (!inGame || turnedThisTick) return;
        if (rightDirection) {        // right -> down
            setHeading(false, false, false, true);
        } else if (downDirection) {  // down -> left
            setHeading(true, false, false, false);
        } else if (leftDirection) {  // left -> up
            setHeading(false, false, true, false);
        } else {                     // up -> right
            setHeading(false, true, false, false);
        }
        turnedThisTick = true;
    }

    private void setHeading(boolean left, boolean right, boolean up, boolean down) {
        leftDirection = left;
        rightDirection = right;
        upDirection = up;
        downDirection = down;
    }

    // Absolute steering, used by keyboard arrows
    public void turnLeft() {
        if (inGame && !rightDirection) {
            leftDirection = true;
            upDirection = false;
            downDirection = false;
        }
    }

    public void turnRight() {
        if (inGame && !leftDirection) {
            rightDirection = true;
            upDirection = false;
            downDirection = false;
        }
    }

    public void turnUp() {
        if (inGame && !downDirection) {
            upDirection = true;
            rightDirection = false;
            leftDirection = false;
        }
    }

    public void turnDown() {
        if (inGame && !upDirection) {
            downDirection = true;
            rightDirection = false;
            leftDirection = false;
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (inGame) {
            checkApple();
            checkCollision();
            move();
            turnedThisTick = false;
        }
        repaint();
    }

    private class TAdapter extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            int key = e.getKeyCode();

            if ((key == KeyEvent.VK_LEFT)) {
                turnLeft();
            }
            if ((key == KeyEvent.VK_RIGHT)) {
                turnRight();
            }
            if ((key == KeyEvent.VK_UP)) {
                turnUp();
            }
            if ((key == KeyEvent.VK_DOWN)) {
                turnDown();
            }
        }
    }
}