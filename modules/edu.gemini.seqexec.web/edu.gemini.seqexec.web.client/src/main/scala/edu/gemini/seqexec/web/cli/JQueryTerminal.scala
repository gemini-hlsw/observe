package edu.gemini.seqexec.web.cli

import org.querki.jquery.JQuery
import org.querki.jsext.{JSOptionBuilder, _}

import scala.scalajs.js

/**
  * Facade for jQuery terminal
  */
object JQueryTerminal {

  @js.native
  trait JsTerminalOptions extends js.Object

  object JsTerminalOptions extends JsTerminalOptionBuilder(noOpts)

  class JsTerminalOptionBuilder(val dict: OptMap) extends JSOptionBuilder[JsTerminalOptions, JsTerminalOptionBuilder](new JsTerminalOptionBuilder(_)) {
    def prompt(t: String) = jsOpt("prompt", t)
    def greeting(t: Boolean) = jsOpt("greeting", t)
    def completion(t: Boolean) = jsOpt("completion", t)
  }

  @js.native
  trait Terminal extends js.Object {
    def echo(s: String):js.Any = js.native
    def error(s: String):js.Any = js.native
  }

  @js.native
    trait TerminalCommands extends JQuery {
    def terminal(c: js.Function2[String, Terminal, js.Any], o: JsTerminalOptions): this.type = js.native
  }

  implicit def jq2Semantic(jq: JQuery): TerminalCommands = jq.asInstanceOf[TerminalCommands]
}
