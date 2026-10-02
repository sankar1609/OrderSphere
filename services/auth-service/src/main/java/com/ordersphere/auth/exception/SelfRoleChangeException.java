package com.ordersphere.auth.exception;

/** An admin tried to change their own role - refused so the last admin can't lock everyone out. */
public class SelfRoleChangeException extends RuntimeException {

  public SelfRoleChangeException() {
    super("You can't change your own role");
  }
}
