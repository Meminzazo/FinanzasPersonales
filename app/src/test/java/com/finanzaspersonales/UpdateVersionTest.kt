package com.finanzaspersonales

import com.finanzaspersonales.data.update.GitHubUpdates
import org.junit.Assert.*
import org.junit.Test

class UpdateVersionTest {
 @Test fun comparesReleaseTagsNumerically() {
  assertTrue(GitHubUpdates.newer("v1.1","1.0"))
  assertTrue(GitHubUpdates.newer("v1.10","1.9"))
  assertTrue(GitHubUpdates.newer("v1.0.1","1.0"))
  assertFalse(GitHubUpdates.newer("v1.0","1.0"))
  assertFalse(GitHubUpdates.newer("v1.9","1.10"))
  assertFalse(GitHubUpdates.newer("bad-tag","1.0"))
 }
}
