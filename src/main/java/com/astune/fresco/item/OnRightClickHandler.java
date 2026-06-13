package com.astune.fresco.item;

import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public interface OnRightClickHandler {
    void onRightClickedBlock(PlayerInteractEvent.RightClickBlock event);
}
