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
import java.util.Scanner;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
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
    private boolean inGame = false; // Starts false until green button pressed
    private boolean gameStartedOnce = false;

    private Timer timer;
    private Image ball;
    private Image apple;
    private Image head;
    
    private static SerialPort serialPort;

    public Board() {
        initBoard();
        setupSerialPort();
    }
    
    private void setupSerialPort() {
        // Change "COM3" to your actual port name if different
        serialPort = SerialPort.getCommPort("COM3");
        serialPort.setBaudRate(9600);

        if (serialPort.openPort()) {
            System.out.println("SUCCESS: Connected to Arduino port!");
            
            // Thread to listen to Arduino buttons
            Thread serialListener = new Thread(() -> {
                Scanner scanner = new Scanner(serialPort.getInputStream());
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    if (line.equals("L")) {
                        turnLeft();
                    } else if (line.equals("R")) {
                        turnRight();
                    } else if (line.equals("START")) {
                        if (!inGame) {
                            initGame();
                        }
                    }
                }
                scanner.close();
            });
            serialListener.start();
        } else {
            System.out.println("ERROR: Could not open serial port. Check connection.");
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