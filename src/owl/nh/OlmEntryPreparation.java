package owl.nh;

/** Entry checks actual state every time; submitting gear/prayer inputs grants no readiness credit. */
final class OlmEntryPreparation {
 enum Need { FINISH_POTTING, MAGIC_GEAR, MAGIC_PROTECTION, MAGIC_BOOST, ENTER }
 static Need need(boolean potted,boolean gear,boolean protection,boolean boost){if(!potted)return Need.FINISH_POTTING;if(!gear)return Need.MAGIC_GEAR;if(!protection)return Need.MAGIC_PROTECTION;if(!boost)return Need.MAGIC_BOOST;return Need.ENTER;}
}
