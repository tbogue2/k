// Copyright (c) 2014 K Team. All Rights Reserved.

package org.kframework.definition

import org.kframework.POSet
import org.kframework.kore.KLabel
import org.kframework.kore.Sort
import org.kframework.kore.K
import org.kframework.attributes._

trait OuterKORE

case class NonTerminalsWithUndefinedSortException(nonTerminals: Set[NonTerminal])
  extends AssertionError(nonTerminals.toString)

case class DivergingAttributesForTheSameKLabel(ps: Set[Production])
  extends AssertionError(ps.toString)

//object NonTerminalsWithUndefinedSortException {
//  def apply(nonTerminals: Set[NonTerminal]) =
//    new NonTerminalsWithUndefinedSortException(nonTerminals.toString, nonTerminals)
//
//}

case class Definition(requires: Set[Require], modules: Set[Module], att: Att = Att())
  extends DefinitionToString with OuterKORE {

  def getModule(name: String): Option[Module] = modules find { case Module(`name`, _, _, _) => true; case _ => false }
}

case class Require(file: java.io.File) extends OuterKORE

case class Module(name: String, imports: Set[Module], localSentences: Set[Sentence], att: Att = Att())
  extends ModuleToString with KLabelMappings with OuterKORE {

  val sentences: Set[Sentence] = localSentences | (imports flatMap { _.sentences })

  val productions: Set[Production] = sentences collect { case p: Production => p }

  val productionsFor: Map[KLabel, Set[Production]] = productions.collect({ case p if p.klabel != None => p }).groupBy(_.klabel.get) map {
    case (l, ps) => (l, ps)
  }

  // Check that productions with the same #klabel have identical attributes
  productionsFor.foreach {
    case (l, ps) => if (ps.groupBy(_.att).size != 1) throw DivergingAttributesForTheSameKLabel(ps)
  }

  val attributesFor: Map[KLabel, Att] = productionsFor mapValues { _.head.att }

  val signatureFor: Map[KLabel, Set[(Seq[Sort], Sort)]] =
    productionsFor mapValues {
      ps: Set[Production] =>
        ps.map {
          p: Production =>
            val params: Seq[Sort] = p.items collect { case NonTerminal(sort) => sort }
            (params, p.sort)
        }
    }

  val sortDeclarations: Set[SyntaxSort] = sentences.collect({ case s: SyntaxSort => s })

  val definedSorts: Set[Sort] = (productions map { _.sort }) ++ (sortDeclarations map { _.sort })

  private lazy val subsortRelations = sentences flatMap {
    case Production(endSort, items, _) =>
      items collect { case NonTerminal(startSort) => (startSort, endSort) }
    case _ => Set()
  }

  lazy val subsorts = POSet(subsortRelations)

  // check that non-terminals have a defined sort
  private val nonTerminalsWithUndefinedSort = sentences flatMap {
    case Production(_, items, _) =>
      items collect { case nt: NonTerminal if !definedSorts.contains(nt.sort) => nt }
    case _ => Set()
  }
  if (!nonTerminalsWithUndefinedSort.isEmpty)
    throw new NonTerminalsWithUndefinedSortException(nonTerminalsWithUndefinedSort)

}

// hooked but different from core, Import is a sentence here

trait Sentence {
  // marker
  val att: Att
}

// deprecated
case class Context(body: K, requires: K, att: Att = Att()) extends Sentence with OuterKORE with ContextToString

case class Rule(body: K, requires: K, ensures: K, att: Att) extends Sentence with RuleToString with OuterKORE

case class ModuleComment(comment: String, att: Att = Att()) extends Sentence with OuterKORE

case class Import(moduleName: String, att: Att = Att()) extends Sentence with ImportToString with OuterKORE

// hooked

// syntax declarations

case class SyntaxPriority(priorities: Seq[Set[Tag]], att: Att = Att())
  extends Sentence with SyntaxPriorityToString with OuterKORE

object Associativity extends Enumeration {
  type Value1 = Value
  val Left, Right, NonAssoc, Unspecified = Value
}

case class SyntaxAssociativity(
  assoc: Associativity.Value,
  tags: collection.immutable.Set[Tag],
  att: Att = Att())
  extends Sentence with SyntaxAssociativityToString with OuterKORE

case class Tag(name: String) extends TagToString with OuterKORE

//trait Production {
//  def sort: Sort
//  def att: Att
//  def items: Seq[ProductionItem]
//  def klabel: Option[KLabel] =
//    att.get(Production.kLabelAttribute).headOption map { case KList(KToken(_, s, _)) => s } map { KLabel(_) }
//}

case class SyntaxSort(sort: Sort, att: Att = Att()) extends Sentence
with SyntaxSortToString with OuterKORE {
  def items = Seq()
}

case class Production(sort: Sort, items: Seq[ProductionItem], att: Att)
  extends Sentence with ProductionToString {
  def klabel: Option[KLabel] = att.get[String]("#klabel") map { org.kframework.kore.ADTConstructors.KLabel(_) }
}

object Production {
  def apply(klabel: KLabel, sort: Sort, items: Seq[ProductionItem], att: Att = Att()): Production = {
    Production(sort, items, att + ("#klabel" -> klabel.name))
  }
  val kLabelAttribute = "klabel"
}

// hooked but problematic, see kast-core.k

sealed trait ProductionItem extends OuterKORE

// marker

case class NonTerminal(sort: Sort) extends ProductionItem
with NonTerminalToString

case class RegexTerminal(regex: String) extends ProductionItem with RegexTerminalToString

case class Terminal(value: String) extends ProductionItem // hooked
with TerminalToString