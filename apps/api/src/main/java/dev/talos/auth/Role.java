package dev.talos.auth;

/**
 * Section 9.3 enum reference. Phase 15: a strict hierarchy, each role implying every permission of
 * the ones after it -- OWNER {@literal >} MAINTAINER {@literal >} REVIEWER {@literal >} VIEWER (see
 * SecurityConfig's {@code RoleHierarchy} bean). VIEWER is read-only; REVIEWER adds approve/reject/
 * request-changes; MAINTAINER adds project/task CRUD and run start; OWNER adds integrations,
 * secrets, user management, and deploy triggers.
 */
public enum Role {
	OWNER, MAINTAINER, REVIEWER, VIEWER
}
