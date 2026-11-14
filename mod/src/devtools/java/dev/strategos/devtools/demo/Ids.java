package dev.strategos.devtools.demo;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.TechId;
import dev.strategos.core.shared.TemplateId;

/** The templates the chapters use. */
final class Ids {
    static final TemplateId WORKER = TemplateSet.WORKER;
    static final TemplateId INFANTRY = TemplateSet.INFANTRY;
    static final TemplateId ARCHER = new TemplateId("strategos:unit/archer");
    static final TemplateId HORSEMAN = new TemplateId("strategos:unit/horseman");
    static final TemplateId SHEEP = new TemplateId("strategos:unit/sheep");
    static final TemplateId PIG = new TemplateId("strategos:unit/pig");
    static final TemplateId CIVIC_CENTRE = new TemplateId("strategos:building/civic_centre");
    static final TemplateId STOREHOUSE = new TemplateId("strategos:building/storehouse");
    static final TemplateId FARMSTEAD = new TemplateId("strategos:building/farmstead");
    static final TemplateId FIELD = new TemplateId("strategos:building/field");
    static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    static final TemplateId BARRACKS = new TemplateId("strategos:building/barracks");
    static final TemplateId IRON_MINE = new TemplateId("strategos:building/iron_mine");
    static final TemplateId TOWER = new TemplateId("strategos:building/tower");
    static final TemplateId ARSENAL = new TemplateId("strategos:building/arsenal");
    static final TemplateId RAM = new TemplateId("strategos:unit/ram");
    static final TechId WOOD_1 = new TechId("strategos:gather_wood_1");
    static final TechId PHASE_TOWN = new TechId("strategos:phase_town");
    static final TemplateId QUARRY = new TemplateId("strategos:building/quarry");
    /** Gaia's slot in a two-faction match: the animals'. */
    static final int GAIA = 2;

    private Ids() {}
}
