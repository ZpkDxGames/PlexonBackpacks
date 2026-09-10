package com.zpkdxgames.plexonbackpacks.integration.economy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class EconomyGatewayFactory {
    private EconomyGatewayFactory() {
    }

    public static EconomyGateway resolve(JavaPlugin plugin) {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy", false,
                    plugin.getServer().getPluginManager().getPlugin("Vault") == null
                            ? plugin.getClass().getClassLoader()
                            : plugin.getServer().getPluginManager().getPlugin("Vault").getClass().getClassLoader());
            @SuppressWarnings({"rawtypes", "unchecked"})
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
            if (registration == null || registration.getProvider() == null) {
                return UnavailableEconomyGateway.INSTANCE;
            }
            return new ReflectiveVaultEconomyGateway(registration.getProvider(), economyClass);
        } catch (ClassNotFoundException | LinkageError exception) {
            return UnavailableEconomyGateway.INSTANCE;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not initialize Vault economy integration", exception);
            return UnavailableEconomyGateway.INSTANCE;
        }
    }

    private enum UnavailableEconomyGateway implements EconomyGateway {
        INSTANCE;

        @Override
        public boolean available() {
            return false;
        }

        @Override
        public String providerName() {
            return "NONE";
        }

        @Override
        public boolean has(UUID playerId, double amount) {
            return amount <= 0.0D;
        }

        @Override
        public boolean withdraw(UUID playerId, double amount) {
            return amount <= 0.0D;
        }

        @Override
        public boolean refund(UUID playerId, double amount) {
            return amount <= 0.0D;
        }
    }

    private static final class ReflectiveVaultEconomyGateway implements EconomyGateway {
        private final Object provider;
        private final Method has;
        private final Method withdraw;
        private final Method deposit;
        private final Method success;

        private ReflectiveVaultEconomyGateway(Object provider, Class<?> economyClass) {
            this.provider = provider;
            try {
                this.has = economyClass.getMethod("has", OfflinePlayer.class, double.class);
                this.withdraw = economyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
                this.deposit = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
                Class<?> response = withdraw.getReturnType();
                this.success = response.getMethod("transactionSuccess");
            } catch (NoSuchMethodException exception) {
                throw new IllegalStateException("Unsupported Vault economy API", exception);
            }
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public String providerName() {
            return provider.getClass().getSimpleName();
        }

        @Override
        public boolean has(UUID playerId, double amount) {
            if (amount <= 0.0D) {
                return true;
            }
            try {
                return (boolean) has.invoke(provider, Bukkit.getOfflinePlayer(playerId), amount);
            } catch (IllegalAccessException | InvocationTargetException exception) {
                return false;
            }
        }

        @Override
        public boolean withdraw(UUID playerId, double amount) {
            return transact(withdraw, playerId, amount);
        }

        @Override
        public boolean refund(UUID playerId, double amount) {
            return transact(deposit, playerId, amount);
        }

        private boolean transact(Method method, UUID playerId, double amount) {
            if (amount <= 0.0D) {
                return true;
            }
            try {
                Object response = method.invoke(provider, Bukkit.getOfflinePlayer(playerId), amount);
                return response != null && (boolean) success.invoke(response);
            } catch (IllegalAccessException | InvocationTargetException exception) {
                return false;
            }
        }
    }
}
