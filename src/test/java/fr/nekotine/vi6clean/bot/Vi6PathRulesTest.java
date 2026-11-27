package fr.nekotine.vi6clean.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.nekotine.core.navigation.BlockView;
import fr.nekotine.core.navigation.Passage;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

/**
 * Story 5.4: the box and gate semantics of {@link Vi6PathRules}, on hand-built
 * boxes, with no server. Which boxes each role forbids is checked in
 * {@code Vi6PathRulesIT}, on a loaded map.
 */
class Vi6PathRulesTest {

	/** Blocks 10..12 along x, 64..66 along y, 0..1 along z. */
	private static final BoundingBox EXIT = new BoundingBox(10, 64, 0, 13, 67, 2);

	/** An entrance blocking box: blocks 20..21, 64..65, 0. */
	private static final BoundingBox BLOCKING = new BoundingBox(20, 64, 0, 22, 66, 1);

	/** An entrance trigger box, never forbidden: blocks 30..31, 64, 0. */
	private static final BoundingBox TRIGGER = new BoundingBox(30, 64, 0, 32, 65, 1);

	/** A gate: blocks 40, 64..65, 0..2. */
	private static final BoundingBox GATE = new BoundingBox(40, 64, 0, 41, 66, 3);

	private static BlockData block(Material material) {
		return (BlockData) Proxy.newProxyInstance(Vi6PathRulesTest.class.getClassLoader(),
				new Class<?>[]{BlockData.class},
				(proxy, method, args) -> method.getName().equals("getMaterial") ? material : null);
	}

	/** A view holding the material everywhere. */
	private static BlockView everywhere(Material material) {
		var data = block(material);
		return (x, y, z) -> data;
	}

	/**
	 * {@code ForcefieldHandler.FIELD_MATERIAL}, which a plain test cannot load (its
	 * superclass resolves the plugin in its static initializer).
	 */
	private static final Material FIELD_MATERIAL = Material.GRAY_STAINED_GLASS;

	private static final BlockView FIELD = everywhere(FIELD_MATERIAL);

	@Test
	void forbiddenBoxesRefuseTheirCellsAndTriggerBoxesDoNot() {
		var rule = Vi6PathRules.of(List.of(EXIT, BLOCKING), List.of(), FIELD_MATERIAL);
		assertFalse(rule.allows(FIELD, 10, 64, 0), "exit corner");
		assertFalse(rule.allows(FIELD, 12, 66, 1), "exit far corner");
		assertFalse(rule.allows(FIELD, 21, 65, 0), "blocking box");
		assertTrue(rule.allows(FIELD, 30, 64, 0), "trigger box");
		assertTrue(rule.allows(FIELD, 0, 64, 0), "elsewhere");
		assertEquals(0, rule.cost(FIELD, 0, 64, 0));
	}

	@Test
	void aBoxThatOnlyTouchesAFaceDoesNotRefuse() {
		var rule = Vi6PathRules.of(List.of(EXIT), List.of(), FIELD_MATERIAL);
		// maxX == 13: the cell [13, 14] only touches the box.
		assertTrue(rule.allows(FIELD, 13, 64, 0));
		// minX == 10: the cell [9, 10] only touches it.
		assertTrue(rule.allows(FIELD, 9, 64, 0));
		assertTrue(rule.allows(FIELD, 10, 67, 0), "above the box");
		assertTrue(rule.allows(FIELD, 10, 63, 0), "below the box");
		// A box over half a block still refuses the cell it overlaps.
		var half = Vi6PathRules.of(List.of(new BoundingBox(5.5, 64, 0, 6, 65, 1)), List.of(), FIELD_MATERIAL);
		assertFalse(half.allows(FIELD, 5, 64, 0));
		assertTrue(half.allows(FIELD, 6, 64, 0));
	}

	@Test
	void theRuleKeepsItsCopyWhenTheMapBoxChanges() {
		var exit = EXIT.clone();
		var gate = GATE.clone();
		var rule = Vi6PathRules.of(List.of(exit), List.of(gate), FIELD_MATERIAL);
		exit.expand(5);
		gate.expand(5);
		assertTrue(rule.allows(FIELD, 13, 64, 0), "the expanded exit refuses");
		assertEquals(Passage.DEFAULT, rule.passage(FIELD, 41, 64, 0), "the expanded gate opens");
		exit.resize(0, 0, 0, 1, 1, 1);
		assertFalse(rule.allows(FIELD, 10, 64, 0), "the moved exit no longer refuses");
	}

	@Test
	void fieldMaterialInAGateIsOpen() {
		var rule = Vi6PathRules.of(List.of(), List.of(GATE), FIELD_MATERIAL);
		assertEquals(Passage.OPEN, rule.passage(FIELD, 40, 64, 0));
		assertEquals(Passage.OPEN, rule.passage(FIELD, 40, 65, 2));
		assertTrue(rule.allows(FIELD, 40, 64, 0), "a gate is not forbidden");
	}

	@Test
	void fieldMaterialOutsideAGateAndOtherBlocksInAGateAreDefault() {
		var rule = Vi6PathRules.of(List.of(), List.of(GATE), FIELD_MATERIAL);
		assertEquals(Passage.DEFAULT, rule.passage(FIELD, 41, 64, 0), "next to the gate");
		assertEquals(Passage.DEFAULT, rule.passage(FIELD, 40, 66, 0), "above the gate");
		assertEquals(Passage.DEFAULT, rule.passage(everywhere(Material.GLASS), 40, 64, 0), "other glass");
		assertEquals(Passage.DEFAULT, rule.passage(everywhere(Material.AIR), 40, 64, 0), "opened field");
		assertEquals(Passage.DEFAULT, rule.passage((x, y, z) -> null, 40, 64, 0), "not in the view");
	}

	@Test
	void aRuleWithNoGatesOverridesNothing() {
		var thief = Vi6PathRules.of(List.of(EXIT), List.of(), FIELD_MATERIAL);
		assertEquals(Passage.DEFAULT, thief.passage(FIELD, 40, 64, 0));
		var any = Vi6PathRules.of(List.of(), List.of(), FIELD_MATERIAL);
		assertTrue(any.allows(FIELD, 10, 64, 0));
		assertEquals(Passage.DEFAULT, any.passage(FIELD, 40, 64, 0));
	}

	@Test
	void nullBoxesAreSkippedAndNullArgumentsThrow() {
		var boxes = new ArrayList<BoundingBox>();
		boxes.add(null);
		boxes.add(EXIT);
		var rule = Vi6PathRules.of(boxes, boxes, FIELD_MATERIAL);
		assertFalse(rule.allows(FIELD, 10, 64, 0));
		assertThrows(NullPointerException.class, () -> Vi6PathRules.of(null, List.of(), FIELD_MATERIAL));
		assertThrows(NullPointerException.class, () -> Vi6PathRules.of(List.of(), null, FIELD_MATERIAL));
		assertThrows(NullPointerException.class, () -> Vi6PathRules.of(List.of(), List.of(), null));
		assertThrows(NullPointerException.class, () -> Vi6PathRules.guard(null));
		assertThrows(NullPointerException.class, () -> Vi6PathRules.thiefOutside(null));
		assertThrows(NullPointerException.class, () -> Vi6PathRules.thiefInside(null, true));
	}
}
