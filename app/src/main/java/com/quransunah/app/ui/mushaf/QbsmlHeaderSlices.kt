package com.quransunah.app.ui.mushaf

internal data class HeaderGlyphSlice(
    val glyph: String,
    val left: Float = 0f, val top: Float = 0f,
    val right: Float = 1f, val bottom: Float = 1f,
)

/** Small qbsml header glyphs only; separate names inside combined glyphs by their ink bounds.
 * Regions measured from public/fonts/qbsml.ttf (SHA-256 b6798c02ea1fa92e48dd00f6e88213c62c64bd4d46d68e18afc38eb2e27727dd).
 * Left/right follow physical ink coordinates; the first surah is the rightmost name.
 */
internal object QbsmlHeaderSlices {
    private data class Ink(val left: Float, val top: Float, val right: Float, val bottom: Float)
    private data class Group(val surahs: List<Int>, val ink: List<Ink>)
    private val groups = listOf(
        Group(listOf(86, 87), listOf(Ink(0.558268f, 0.050473f, 1.000000f, 0.998423f), Ink(0.000000f, 0.000000f, 0.419723f, 1.000000f))),
        Group(listOf(91, 92), listOf(Ink(0.539686f, 0.068353f, 1.000000f, 0.921394f), Ink(0.000000f, 0.000000f, 0.396118f, 1.000000f))),
        Group(listOf(93, 94), listOf(Ink(0.564683f, 0.000000f, 1.000000f, 0.833333f), Ink(0.000000f, 0.087615f, 0.424242f, 1.000000f))),
        Group(listOf(95, 96), listOf(Ink(0.583012f, 0.038117f, 1.000000f, 1.000000f), Ink(0.000000f, 0.000000f, 0.435495f, 0.968610f))),
        Group(listOf(97, 98), listOf(Ink(0.549099f, 0.000000f, 1.000000f, 0.886503f), Ink(0.000000f, 0.000000f, 0.412411f, 1.000000f))),
        Group(listOf(99, 100), listOf(Ink(0.613347f, 0.000000f, 1.000000f, 1.000000f), Ink(0.000000f, 0.067700f, 0.485311f, 0.973083f))),
        Group(listOf(101, 102), listOf(Ink(0.541348f, 0.057554f, 1.000000f, 0.922302f), Ink(0.000000f, 0.000000f, 0.416966f, 1.000000f))),
        Group(listOf(103, 104, 105), listOf(Ink(0.726132f, 0.000000f, 1.000000f, 1.000000f), Ink(0.353634f, 0.000000f, 0.640822f, 0.846786f), Ink(0.000000f, 0.000000f, 0.268402f, 0.991031f))),
        Group(listOf(106, 107, 108), listOf(Ink(0.745843f, 0.000000f, 1.000000f, 1.000000f), Ink(0.392432f, 0.069111f, 0.664031f, 0.858956f), Ink(0.000000f, 0.038787f, 0.310620f, 0.949929f))),
        Group(listOf(109, 110, 111), listOf(Ink(0.679713f, 0.000000f, 1.000000f, 0.896480f), Ink(0.332614f, 0.073154f, 0.594422f, 1.000000f), Ink(0.000000f, 0.103520f, 0.247400f, 0.868185f))),
        Group(listOf(112, 113, 114), listOf(Ink(0.679210f, 0.027126f, 1.000000f, 1.000000f), Ink(0.339416f, 0.000000f, 0.595656f, 0.949413f), Ink(0.000000f, 0.016129f, 0.255863f, 0.848240f))),
    )

    fun forSurah(number: Int, singleGlyph: String, combinedGlyph: (List<Int>) -> String): HeaderGlyphSlice? {
        if (singleGlyph.isNotEmpty()) return HeaderGlyphSlice(singleGlyph)
        val group = groups.firstOrNull { number in it.surahs } ?: return null
        val ink = group.ink[group.surahs.indexOf(number)]
        val glyph = combinedGlyph(group.surahs).takeIf { it.isNotEmpty() } ?: return null
        return HeaderGlyphSlice(glyph, ink.left, ink.top, ink.right, ink.bottom)
    }
}
