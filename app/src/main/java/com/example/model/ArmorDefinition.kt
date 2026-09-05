package com.example.model

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.R

/**
 * Unique identifier for armor types
 */
enum class ArmorType(val id: String) {
    DARK_KNIGHT("dark_knight"),
    CYBER_KNIGHT("cyber_knight")
}

/**
 * Definition of an equipable AR Armor set.
 * Designed to easily extend with additional armors (Armor 3, 4, etc.).
 */
data class ArmorDefinition(
    val type: ArmorType,
    val name: String,
    val subtitle: String,
    val description: String,
    val category: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val accentColor: Color,
    val emissiveColor: Color,
    val glowIntensity: Float = 1.0f,
    val hasCape: Boolean = false,
    val hasWeapon: Boolean = false,
    val weaponName: String? = null,
    val statsDefense: Int = 95,
    val statsAgility: Int = 80,
    val statsPower: Int = 90,
    @DrawableRes val previewDrawable: Int
) {
    companion object {
        val ALL_ARMORS = listOf(
            ArmorDefinition(
                type = ArmorType.DARK_KNIGHT,
                name = "DARK KNIGHT",
                subtitle = "Dark Fantasy Heavy Plate",
                description = "Silver-steel sculpted heavy plate with gothic pointed cowl, ornate pauldrons, flowing midnight cape, and massive two-handed greatsword.",
                category = "MEDIEVAL FANTASY",
                primaryColor = Color(0xFFE2E8F0),      // Polished Silver Steel
                secondaryColor = Color(0xFF181F2C),    // Obsidian Charcoal Underplate
                accentColor = Color(0xFF475569),       // Dark Gothic Steel Trim
                emissiveColor = Color(0xFFCBD5E1),     // Steel Specular Glow
                glowIntensity = 0.4f,
                hasCape = true,
                hasWeapon = true,
                weaponName = "Gothic Greatsword",
                statsDefense = 98,
                statsAgility = 72,
                statsPower = 96,
                previewDrawable = R.drawable.armor_dark_knight_preview
            ),
            ArmorDefinition(
                type = ArmorType.CYBER_KNIGHT,
                name = "CYBER KNIGHT",
                subtitle = "High-Tech Sci-Fi Combat Exosuit",
                description = "Aerodynamic nano-composite white carapace over carbon-fiber mesh, full aerodynamic helmet, and pulsing neon cyan energy conduit lines.",
                category = "SCI-FI EXOSUIT",
                primaryColor = Color(0xFFF8FAFC),      // Nanotech High-Gloss Ceramic White
                secondaryColor = Color(0xFF0F172A),    // Carbon Fiber Black
                accentColor = Color(0xFF334155),       // Titanium Joint Accents
                emissiveColor = Color(0xFF00F0FF),     // Emissive Neon Cyan Energy Lines
                glowIntensity = 1.0f,
                hasCape = false,
                hasWeapon = false,
                weaponName = null,
                statsDefense = 92,
                statsAgility = 96,
                statsPower = 94,
                previewDrawable = R.drawable.armor_cyber_knight_preview
            )
        )

        fun getByType(type: ArmorType): ArmorDefinition {
            return ALL_ARMORS.firstOrNull { it.type == type } ?: ALL_ARMORS.first()
        }
    }
}
