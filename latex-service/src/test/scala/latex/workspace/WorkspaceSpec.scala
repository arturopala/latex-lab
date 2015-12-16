package latex.workspace

import org.scalatest.{ Finders, FlatSpecLike, Matchers }

import org.scalatest.prop.PropertyChecks
import org.scalacheck.Gen._

import latex._
import workspace._

class WorkspaceSpec extends FlatSpecLike with Matchers with PropertyChecks {

  val workspace: Workspace = new Module().root / "test"

  "Workspace" should "list files in root location" in {
    workspace.list should contain theSameElementsAs Seq("ExampleProjectTest.bib", "ExampleProjectTest.jpg", "BlankProjectTest.tex", "test.tex", "images/test.jpg")
  }

  it should "return workspace location" in {
    workspace.location should not be null
  }

  it should "have route defined" in {
    workspace.route should not be null
  }

  it should "check if file exists" in {
    workspace.exists("test.tex") shouldBe true
    workspace.exists("ExampleProjectTest.jpg") shouldBe true
    workspace.exists("BlankProjectTest.tex") shouldBe true
    workspace.exists("ExampleProjectTest.bib") shouldBe true
    workspace.exists("test.log") shouldBe true
    workspace.exists("foo.foo") shouldBe false
    workspace.exists("bar/foo.foo") shouldBe false
  }

  it should "return url of public file" in {
    workspace.urlOf("test.tex") shouldBe Some("/resources/test/test.tex")
    workspace.urlOf("test.log") shouldBe None
    workspace.urlOf("ExampleProjectTest.jpg") shouldBe Some("/resources/test/ExampleProjectTest.jpg")
    workspace.urlOf("ExampleProjectTest.bib") shouldBe Some("/resources/test/ExampleProjectTest.bib")
  }
}