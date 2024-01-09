package fr.nekotine.vi6clean.impl.status;

public enum InvisibilityType {
	True(1),
	Silent(2),
	Default(3);

	private int rank;
	InvisibilityType(int rank) {
		this.rank = rank;
	}
	public int getRank() {
		return rank;
	}
}
