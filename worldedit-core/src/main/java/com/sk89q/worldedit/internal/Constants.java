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

package com.sk89q.worldedit.internal;

import java.util.List;

public final class Constants {

    private Constants() {
    }

    /**
     * List of top level NBT fields that should not be copied to a world,
     * such as UUIDLeast and UUIDMost.
     */
    public static final List<String> NO_COPY_ENTITY_NBT_FIELDS;

    static {
        NO_COPY_ENTITY_NBT_FIELDS = List.of(
                "UUIDLeast", "UUIDMost", "UUID", // Bukkit and Vanilla
                "WorldUUIDLeast", "WorldUUIDMost", // Bukkit and Vanilla
                "PersistentIDMSB", "PersistentIDLSB" // Forge
        );
    }

    /**
     * The DataVersion for Minecraft 1.13
     * @deprecated If Fawe drops interaction with 1.13, this method is subject to removal.
     */
    @Deprecated(forRemoval = true, since = "2.0.0")
    public static final int DATA_VERSION_MC_1_13 = 1519;

    /**
     * The DataVersion for Minecraft 1.13.2
     * @deprecated If Fawe drops interaction with 1.13, this method is subject to removal.
     */
    @Deprecated(forRemoval = true, since = "2.0.0")
    public static final int DATA_VERSION_MC_1_13_2 = 1631;

    /**
     * The DataVersion for Minecraft 1.15
     */
    public static final int DATA_VERSION_MC_1_15 = 2225;

    /**
     * The DataVersion for Minecraft 1.16
     */
    public static final int DATA_VERSION_MC_1_16 = 2566;

    /**
     * The DataVersion for Minecraft 1.17
     */
    public static final int DATA_VERSION_MC_1_17 = 2724;

    /**
     * The DataVersion for Minecraft 1.18
     */
    public static final int DATA_VERSION_MC_1_18 = 2860;

    /**
     * The DataVersion for Minecraft 1.19
     */
    public static final int DATA_VERSION_MC_1_19 = 3105;

    /**
     * The DataVersion for Minecraft 1.20
     */
    public static final int DATA_VERSION_MC_1_20 = 3463;

    /**
     * The DataVersion for Minecraft 1.21
     */
    public static final int DATA_VERSION_MC_1_21 = 3953;

    /**
     * The DataVersion for Minecraft 1.21.2
     */
    public static final int DATA_VERSION_MC_1_21_2 = 4080;

    /**
     * The DataVersion for Minecraft 1.21.3
     */
    public static final int DATA_VERSION_MC_1_21_3 = 4082;

    /**
     * The DataVersion for Minecraft 1.21.4
     */
    public static final int DATA_VERSION_MC_1_21_4 = 4189;

    /**
     * The DataVersion for Minecraft 1.21.5
     */
    public static final int DATA_VERSION_MC_1_21_5 = 4325;

    /**
     * The DataVersion for Minecraft 1.21.6
     */
    public static final int DATA_VERSION_MC_1_21_6 = 4435;

    /**
     * The DataVersion for Minecraft 1.21.7
     */
    public static final int DATA_VERSION_MC_1_21_7 = 4438;

    /**
     * The DataVersion for Minecraft 1.21.8
     */
    public static final int DATA_VERSION_MC_1_21_8 = 4440;
}
