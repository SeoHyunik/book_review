package com.example.bookreview.util;

import org.jasypt.util.text.AES256TextEncryptor;

public class JasyptEncryptMain {
    public static void main(String[] args) {
        String password = "open_ai_secret";

        String plain = "sk-XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX";
        AES256TextEncryptor encryptor = new AES256TextEncryptor();
        encryptor.setPassword(password);

        String encrypted = encryptor.encrypt(plain);
        System.out.println("ENC(" + encrypted + ")");
    }
}
