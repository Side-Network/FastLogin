/*
 * SPDX-License-Identifier: MIT
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 games647 and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.games647.fastlogin.bungee.listener;

import com.github.games647.fastlogin.bungee.BungeeLoginSession;
import com.github.games647.fastlogin.bungee.FastLoginBungee;
import com.github.games647.fastlogin.bungee.task.AsyncToggleMessage;
import com.github.games647.fastlogin.core.hooks.bedrock.FloodgateService;
import com.github.games647.fastlogin.core.message.ChangePremiumMessage;
import com.github.games647.fastlogin.core.shared.FastLoginCore;
import com.github.games647.fastlogin.core.storage.StoredProfile;
import me.drepic.proton.common.message.MessageHandler;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

public class ProtonListenerBungee {

    private final FastLoginBungee plugin;

    public ProtonListenerBungee(FastLoginBungee plugin) {
        this.plugin = plugin;
    }

    @MessageHandler(namespace = "FastLogin", subject = "success")
    public void onPlayerSuccess(String playerName) {
        ProxiedPlayer player = plugin.getProxy().getPlayer(playerName);
        if (player == null) {
            return;
        }

        onSuccessMessage(player);
    }

    @MessageHandler(namespace = "FastLogin", subject = "change-premium")
    public void onPlayerChangePremium(ChangePremiumMessage changeMessage) {
        ProxiedPlayer forPlayer = plugin.getProxy().getPlayer(changeMessage.getForPlayer());
        if (forPlayer == null) {
            return;
        }

        FastLoginCore<ProxiedPlayer, CommandSender, FastLoginBungee> core = plugin.getCore();
        String playerName = changeMessage.getPlayerName();
        boolean isSourceInvoker = changeMessage.isSourceInvoker();

        if (changeMessage.shouldEnable()) {
            if (playerName.equals(forPlayer.getName()) && plugin.getCore().getConfig().get("premium-warning", true)
                    && !core.getPendingConfirms().contains(forPlayer.getUniqueId())) {
                String message = core.getMessage("premium-warning");
                forPlayer.sendMessage(TextComponent.fromLegacyText(message));
                core.getPendingConfirms().add(forPlayer.getUniqueId());
                return;
            }

            core.getPendingConfirms().remove(forPlayer.getUniqueId());
            Runnable task = new AsyncToggleMessage(core, forPlayer, playerName, true, isSourceInvoker);
            plugin.getScheduler().runAsync(task);
        } else {
            Runnable task = new AsyncToggleMessage(core, forPlayer, playerName, false, isSourceInvoker);
            plugin.getScheduler().runAsync(task);
        }
    }

    private void onSuccessMessage(ProxiedPlayer forPlayer) {
        boolean shouldPersist = forPlayer.getPendingConnection().isOnlineMode();

        FloodgateService floodgateService = plugin.getFloodgateService();
        if (!shouldPersist && floodgateService != null) {
            // always save floodgate players to lock this username
            shouldPersist = floodgateService.isBedrockPlayer(forPlayer.getUniqueId());
        }

        if (shouldPersist) {
            //bukkit module successfully received and force logged in the user
            //update only on success to prevent corrupt data
            BungeeLoginSession loginSession = plugin.getSession().get(forPlayer.getPendingConnection());
            StoredProfile playerProfile = loginSession.getProfile();
            loginSession.setRegistered(true);

            if (!loginSession.isAlreadySaved()) {
                playerProfile.setOnlinemodePreferred(true);
                plugin.getCore().getStorage().save(playerProfile);
                loginSession.setAlreadySaved(true);
            }
        }
    }
}
