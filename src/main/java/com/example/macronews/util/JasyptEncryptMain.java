package com.example.macronews.util;

import org.jasypt.util.text.AES256TextEncryptor;

public class JasyptEncryptMain {
    public static void main(String[] args) {
        String password = System.getenv("JASYPT_PASSWORD");
        if (password == null || password.isBlank()) {
            password = System.getProperty("jasypt.password");
        }

        if (password == null || password.isBlank() || args.length == 0 || args[0].isBlank()) {
            System.out.println("Usage: set JASYPT_PASSWORD (or -Djasypt.password=...) and pass plaintext as the first argument.");
            return;
        }

        String plain = args[0];
        AES256TextEncryptor encryptor = new AES256TextEncryptor();
        encryptor.setPassword(password);

        String encrypted = encryptor.encrypt(plain);
        System.out.println("ENC(" + encrypted + ")");
    }
}
