package dev.talos.auth;

/** Section 9.3 enum reference. RBAC enforcement arrives with multi-user support (post-MVP); MVP runs owner-mode. */
public enum Role {
	OWNER, MAINTAINER, REVIEWER, VIEWER
}
