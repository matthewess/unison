package org.unisonweb.codegeneration

object FunctionApplicationGenerator extends OneFileGenerator("FunctionApplication.scala") {
  def source =
    "package org.unisonweb.compilation" <>
    "" <>
    "import org.unisonweb.Term.Term" <>
    "" <>
    b("trait FunctionApplication") {
      bEqExpr("def staticCall(fn: Lambda, args: Array[Computation], decompile: Term, isTail: Boolean): Computation") {
        "if (isTail) staticTailCall(fn, args, decompile)" <>
        "else staticNonTail(fn, args, decompile)"
      } <>
      "" <>
      bEqExpr("def staticRecCall(args: Array[Computation], decompile: Term, isTail: Boolean): Computation") {
        "if (isTail) staticRecTailCall(args, decompile)" <>
        "else staticRecNonTail(args, decompile)"
      } <>
      "" <>
      bEqExpr("def dynamicCall(fn: Lambda, args: Array[Computation], decompile: Term, isTail: Boolean): Computation") {
        "if (isTail) dynamicTailCall(fn, args, decompile)" <>
        "else dynamicNonTailCall(fn, args, decompile)"
      }

      "" <> sourceStatic("staticNonTail",
        classPrefix = "StaticNonTail",
        declArgsPrefix = Some("fn: Lambda"),
        evalFn = "fn",
        evalArgsPrefix = Some("fn")
      ) <>
      "" <> sourceStatic("staticTailCall",
        classPrefix = "StaticTailCall",
        declArgsPrefix = Some("fn: Lambda"),
        evalFn = "tailCall",
        evalArgsPrefix = Some("fn")
      ) <>
      "" <> sourceStatic("staticRecNonTail",
        classPrefix = "StaticRecNonTail",
        declArgsPrefix = None,
        evalFn = "rec",
        evalArgsPrefix = Some("rec")
      ) <>
      "" <> sourceStatic("staticRecTailCall",
        classPrefix = "StaticRecTailCall",
        declArgsPrefix = None,
        evalFn = "selfTailCall",
        evalArgsPrefix = None
      ) <>
      "" <> sourceDynamic(isTail = false) <>
      "" <> sourceDynamic(isTail = true)
    }

  def sourceStatic(defName: String, classPrefix: String, declArgsPrefix: Option[String], evalFn: String, evalArgsPrefix: Option[String]) = {
    val evalArgsPrefixStr = evalArgsPrefix.map(_ + ", ").getOrElse("")
    def eEvalArgs(argCount: Int) =
      evalArgsPrefixStr + (argCount - 1 to 0 by -1).commas(i => s"e${i}, e${i}b") + commaIf(argCount) + "r"

    def xEvalArgs(argCount: Int) =
      "rec, " + (0 until argCount).commas(i => s"x${i}, x${i}b") + commaIf(argCount) + "r"

    bEq(s"def $defName(" + declArgsPrefix.map(_ + ", ").getOrElse("") + "args: Array[Computation], decompile: Term): Computation") {
      "val stackSize = args.map(_.stackSize).max" <>
      switch("stackSize") {
          (0 to maxInlineStack).each { stackSize =>
            `case`(stackSize) {
              switch("args.length") {
                (1 to maxInlineArgs).each { argCount =>
                  `case`(argCount) {
                    (0 until argCount).each { i => s"val arg$i = args($i)" } <>
                    b(s"class ${classPrefix}S${stackSize}A${argCount} extends Computation${stackSize}(decompile)") {
                      bEq(applySignature(stackSize)) {
                        (0 until argCount).each(i => (
                          s"val e$i = "
                            + catchTC(s"arg$i(${xEvalArgs(stackSize)})")
                            + s"; val e${i}b = r.boxed")) <>
                          s"$evalFn(${eEvalArgs(argCount)})"
                      }
                    } <>
                    s"new ${classPrefix}S${stackSize}A${argCount}" <>
                    ""
                  }
                } <>
                `case`("argCount") {
                  b(s"class ${classPrefix}S${stackSize}AN extends Computation${stackSize}(decompile)") {
                    bEq(applySignature(stackSize)) {
                      "val slots = new Array[Slot](argCount)" <>
                      "var i = 0" <>
                      b("while (i < argCount)") {
                        "val slot = slots(argCount - 1 - i)" <>
                        s"slot.unboxed = " + catchTC(s"args(i)(${xEvalArgs(stackSize)})") <>
                        s"slot.boxed = r.boxed" <>
                        "i += 1"
                      } <>
                      s"$evalFn(${evalArgsPrefixStr}slots, r)"
                    }
                  } <>
                  s"new ${classPrefix}S${stackSize}AN"
                }
              }
            }
          } <>
          `case`("stackSize") {
            switch("args.length") {
              (1 to maxInlineArgs).each { argCount =>
                `case`(argCount) {
                  (0 until argCount).each { i => s"val arg$i = args($i)" } <>
                  b(s"class ${classPrefix}SNA$argCount extends ComputationN(stackSize, decompile)") {
                    bEq(applyNSignature) {
                      (0 until argCount).each { i =>
                        s"val e$i = " + catchTC(s"arg$i(rec, xs, r)") + s"; val e${i}b = r.boxed"
                      } <>
                      s"$evalFn(${eEvalArgs(argCount)})"
                    }
                  } <>
                  s"new ${classPrefix}SNA$argCount"
                }
              } <>
              `case`("argCount") {
                b(s"class ${classPrefix}SNAN extends ComputationN(stackSize, decompile)") {
                  bEq(applyNSignature) {
                    "val slots = new Array[Slot](argCount)" <>
                      "var i = 0" <>
                      b("while (i < argCount)") {
                        "val slot = slots(argCount - 1 - i)" <>
                          s"slot.unboxed = " + catchTC(s"args(i)(rec, xs, r)") <>
                          s"slot.boxed = r.boxed" <>
                          "i += 1"
                      } <>
                      s"$evalFn(${evalArgsPrefixStr}slots, r)"
                  }
                } <>
                s"new ${classPrefix}SNAN"
              }
            }
          }
        }
    }
  }

  def sourceDynamic(isTail: Boolean): String = {
    val emptyOrNon = if (isTail) "" else "Non"
    bEq(s"def dynamic${emptyOrNon}TailCall(fn: Computation, args: Array[Computation], decompile: Term): Computation") {
      "val stackSize = args.map(_.stackSize).max" <>
      switch("stackSize") {
        (0 to maxInlineStack).each { i =>
          `case`(i) {
            switch("args.length") {
              (1 to maxInlineArgs).each { j =>
                `case`(j) {
                  val className = s"${emptyOrNon}TailCallS${i}A${j}"
                  b(s"class $className extends Computation$i(decompile)") {
                    (0 until j).each(j => s"val arg$j = args($j)") <>
                    bEq(applySignature(i)) {
                      s"val lambda = ${evalBoxed(i, "fn")}.asInstanceOf[Lambda]" <>
                      (0 until j).each { j => s"val arg${j}r = " + eval(i, s"arg$j") + s"; val arg${j}rb = r.boxed" } <>
                      (if (!isTail)
                        s"lambda(lambda, " + (j-1 to 0 by -1).commas(j => s"arg${j}r, arg${j}rb") + commaIf(j) + "r)"
                      else "tailCall(lambda, " + (j-1 to 0 by -1).commas(j => s"arg${j}r, arg${j}rb") + commaIf(j) + "r)")
                    }
                  } <>
                  s"new $className"
                }
              } <>
              `case`("argCount") {
                val className = s"${emptyOrNon}TailCallS${i}AN"
                b(s"class $className extends Computation$i(decompile)") {
                  bEq(applySignature(i)) {
                    "val argsr = new Array[Slot](argCount)" <>
                    s"val lambda = ${evalBoxed(i, "fn")}.asInstanceOf[Lambda]" <>
                    "var k = 0" <>
                    b("while (k < argCount)") {
                      "argsr(argCount - 1 - k) = new Slot(" + eval(i, "args(k)") + ", r.boxed)" <>
                      "k += 1"
                    } <>
                    (if (!isTail) "lambda(lambda, argsr, r)"
                    else "tailCall(lambda, argsr, r)")
                  }
                } <>
                s"new $className"
              }
            }
          }
        } <>
        `case`("stackSize") {
          switch("args.length") {
            (1 to maxInlineArgs).each { j =>
              `case`(j) {
                val className = s"${emptyOrNon}TailCallSNA$j"
                b(s"class $className extends ComputationN(stackSize, decompile)") {
                  (0 until j).each(j => s"val arg$j = args($j)") <>
                  bEq(applyNSignature) {
                    s"val lambda = ${evalNBoxed("fn")}.asInstanceOf[Lambda]" <>
                    (0 until j).each( j => s"val arg${j}r = " + evalN(s"arg$j") + s"; val arg${j}rb = r.boxed" ) <>
                    (if (!isTail) s"lambda(lambda, " + ((j-1) to 0 by -1).commas(j => s"arg${j}r, arg${j}rb") + commaIf(j) + "r)"
                    else s"tailCall(lambda, " + ((j-1) to 0 by -1).commas(j => s"arg${j}r, arg${j}rb") + commaIf(j) + "r)")
                  }
                } <>
                s"new $className"
              }
            } <>
            `case`("argCount") {
              val className = s"${emptyOrNon}TailCallSNAM"
              b(s"class $className extends ComputationN(argCount, decompile)") {
                bEq(applyNSignature) {
                  "val argsr = new Array[Slot](argCount)" <>
                  s"val lambda = ${evalNBoxed("fn")}.asInstanceOf[Lambda]" <>
                  "var k = 0" <>
                  b("while (k < argCount)") {
                    "argsr(argCount - 1 - k) = new Slot(" + evalN("args(k)") + ", r.boxed)" <>
                    "k += 1"
                  } <>
                  (if (!isTail) "lambda(lambda, argsr, r)"
                  else "tailCall(lambda, argsr, r)")
                }
              } <>
              s"new $className"
            }
          }
        }
      }
    }
  }
}
