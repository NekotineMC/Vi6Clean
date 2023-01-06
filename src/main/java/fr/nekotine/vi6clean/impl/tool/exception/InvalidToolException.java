package fr.nekotine.vi6clean.impl.tool.exception;

public class InvalidToolException extends RuntimeException {

	public InvalidToolException(String msg) {
		super(msg);
	}
	
	public InvalidToolException(String msg, Throwable cause) {
		super(msg, cause);
	}
	
}
