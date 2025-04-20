/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.internal.cui;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.enginehub.linbus.tree.LinCompoundTag;

import javax.annotation.Nullable;

/**
 * Handles creation of server-side CUI systems.
 */
public class ServerCUIHandler {

    private static final int MAX_DISTANCE = 32;

    private ServerCUIHandler() {
    }

    public static int getMaxServerCuiSize() {
        return Integer.MAX_VALUE;
    }

    /**
     * Creates a structure block that shows the region.
     *
     * <p>
     * Null symbolises removal of the CUI.
     * </p>
     *
     * @param player The player to create the structure block for.
     * @return The structure block, or null
     */
    @Nullable
    public static BaseBlock createStructureBlock(Player player) {
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        RegionSelector regionSelector = session.getRegionSelector(player.getWorld());

        int posX;
        int posY;
        int posZ;
        int width;
        int height;
        int length;

        if (regionSelector instanceof CuboidRegionSelector) {
            if (regionSelector.isDefined()) {
                try {
                    CuboidRegion region = ((CuboidRegionSelector) regionSelector).getRegion();

                    posX = region.getMinimumPoint().x();
                    posY = region.getMinimumPoint().y();
                    posZ = region.getMinimumPoint().z();

                    width = region.getWidth();
                    height = region.getHeight();
                    length = region.getLength();
                } catch (IncompleteRegionException e) {
                    // This will never happen.
                    e.printStackTrace();
                    return null;
                }
            } else {
                CuboidRegion region = ((CuboidRegionSelector) regionSelector).getIncompleteRegion();
                BlockVector3 point;
                if (region.getPos1() != null) {
                    point = region.getPos1();
                } else if (region.getPos2() != null) {
                    point = region.getPos2();
                } else {
                    // No more selection
                    return null;
                }

                // Just select the point.
                posX = point.x();
                posY = point.y();
                posZ = point.z();
                width = 1;
                height = 1;
                length = 1;
            }
        } else {
            // We only support cuboid regions right now.
            return null;
        }

        int maxSize = getMaxServerCuiSize();

        if (width > maxSize || length > maxSize || height > maxSize) {
            // Structure blocks have a limit of maxSize^3
            return null;
        }

        LinCompoundTag.Builder structureTag = LinCompoundTag.builder();
        structureTag.putString("name", "worldedit:" + player.getName());
        structureTag.putString("author", player.getName());
        structureTag.putString("metadata", "");
        structureTag.putInt("x", posX);
        structureTag.putInt("y", posY);
        structureTag.putInt("z", posZ);
        structureTag.putInt("posX", posX + width - 1);
        structureTag.putInt("posY", posY + height - 1);
        structureTag.putInt("posZ", posZ + length - 1);
        structureTag.putInt("sizeX", width);
        structureTag.putInt("sizeY", height);
        structureTag.putInt("sizeZ", length);
        structureTag.putString("rotation", "NONE");
        structureTag.putString("mirror", "NONE");
        structureTag.putString("mode", "SAVE");
        structureTag.putByte("ignoreEntities", (byte) 1);
        structureTag.putByte("showboundingbox", (byte) 1);
        structureTag.putString("id", BlockTypes.STRUCTURE_BLOCK.id());

        return BlockTypes.STRUCTURE_BLOCK.getDefaultState().toBaseBlock(structureTag.build());
        //FAWE end
    }

}
