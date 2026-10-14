package com.pcmanager.network;

import com.pcmanager.PCManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {
    public static final String PROTOCOL_VERSION = "1";

    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToServer(
                RebirthC2SPacket.TYPE,
                RebirthC2SPacket.STREAM_CODEC,
                RebirthC2SPacket::handle
        );

        registrar.playToServer(
                RequestCharHistoryC2SPacket.TYPE,
                RequestCharHistoryC2SPacket.STREAM_CODEC,
                RequestCharHistoryC2SPacket::handle
        );

        registrar.playToServer(
                DepositWithdrawStateBrandPacket.TYPE,
                DepositWithdrawStateBrandPacket.STREAM_CODEC,
                DepositWithdrawStateBrandPacket::handle
        );

        registrar.playToServer(
                SelectProfessionPacket.TYPE,
                SelectProfessionPacket.STREAM_CODEC,
                SelectProfessionPacket::handle
        );

        registrar.playToServer(
                ExpandWorldBorderPacket.TYPE,
                ExpandWorldBorderPacket.STREAM_CODEC,
                ExpandWorldBorderPacket::handle
        );

        registrar.playToServer(
                DepositTreasuryPacket.TYPE,
                DepositTreasuryPacket.STREAM_CODEC,
                DepositTreasuryPacket::handle
        );

        registrar.playToServer(
                PayTaxPacket.TYPE,
                PayTaxPacket.STREAM_CODEC,
                PayTaxPacket::handle
        );

        registrar.playToServer(
                SetTaxRatePacket.TYPE,
                SetTaxRatePacket.STREAM_CODEC,
                SetTaxRatePacket::handle
        );

        registrar.playToServer(
                WithdrawTreasuryPacket.TYPE,
                WithdrawTreasuryPacket.STREAM_CODEC,
                WithdrawTreasuryPacket::handle
        );

        registrar.playToServer(
                DepositWithdrawGoldPacket.TYPE,
                DepositWithdrawGoldPacket.STREAM_CODEC,
                DepositWithdrawGoldPacket::handle
        );

        registrar.playToServer(
                LeaveProfessionPacket.TYPE,
                LeaveProfessionPacket.STREAM_CODEC,
                LeaveProfessionPacket::handle
        );

        registrar.playToServer(
                SaveNotePacket.TYPE,
                SaveNotePacket.STREAM_CODEC,
                SaveNotePacket::handle
        );

        registrar.playToServer(
                SendNotePacket.TYPE,
                SendNotePacket.STREAM_CODEC,
                SendNotePacket::handle
        );

        registrar.playToServer(
                ClaimLetterPacket.TYPE,
                ClaimLetterPacket.STREAM_CODEC,
                ClaimLetterPacket::handle
        );

        registrar.playToServer(
                EnactLawPacket.TYPE,
                EnactLawPacket.STREAM_CODEC,
                EnactLawPacket::handle
        );

        registrar.playToServer(
                EditLawPacket.TYPE,
                EditLawPacket.STREAM_CODEC,
                EditLawPacket::handle
        );

        registrar.playToServer(
                RepealLawPacket.TYPE,
                RepealLawPacket.STREAM_CODEC,
                RepealLawPacket::handle
        );

        registrar.playToClient(
                SyncAllCharactersPacket.TYPE,
                SyncAllCharactersPacket.STREAM_CODEC,
                SyncAllCharactersPacket::handle
        );

        registrar.playToClient(
                OpenCharHistoryPacket.TYPE,
                OpenCharHistoryPacket.STREAM_CODEC,
                OpenCharHistoryPacket::handle
        );

        registrar.playToClient(
                OpenRebirthScreenS2CPacket.TYPE,
                OpenRebirthScreenS2CPacket.STREAM_CODEC,
                OpenRebirthScreenS2CPacket::handle
        );

        PCManager.LOGGER.info("Registered network payloads for P&C Manager.");
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void broadcastToAll(CustomPacketPayload packet) {
        PacketDistributor.sendToAllPlayers(packet);
    }

    public static void sendToServer(CustomPacketPayload packet) {
        PacketDistributor.sendToServer(packet);
    }
}
