package ru.voidrp.gamesync.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public final class ItemStackSnapshotService {

    public String serializeSingle(ItemStack source) {
        if (source == null) {
            throw new IllegalArgumentException("source item must not be null");
        }
        ItemStack clone = source.clone();
        clone.setAmount(1);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream stream = new BukkitObjectOutputStream(output)) {
                stream.writeObject(clone);
            }
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Не удалось сохранить предмет для рынка: " + exception.getMessage(), exception);
        }
    }

    public ItemStack deserialize(String base64, int amount) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalArgumentException("empty item snapshot");
        }
        try {
            byte[] raw = Base64.getDecoder().decode(base64);
            try (BukkitObjectInputStream stream = new BukkitObjectInputStream(new ByteArrayInputStream(raw))) {
                Object value = stream.readObject();
                if (!(value instanceof ItemStack item)) {
                    throw new IllegalStateException("snapshot does not contain ItemStack");
                }
                ItemStack clone = item.clone();
                clone.setAmount(Math.max(1, amount));
                return clone;
            }
        } catch (IOException | ClassNotFoundException exception) {
            throw new IllegalStateException("Не удалось восстановить предмет рынка: " + exception.getMessage(), exception);
        }
    }
}
