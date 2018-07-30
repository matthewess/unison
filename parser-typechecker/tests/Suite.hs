{-# OPTIONS_GHC -fno-warn-incomplete-patterns #-}

module Main where

import EasyTest
import System.IO
import System.Environment (getArgs)
import qualified Unison.Test.DataDeclaration as DataDeclaration
import qualified Unison.Test.FileParser as FileParser
import qualified Unison.Test.Lexer as Lexer
import qualified Unison.Test.TermParser as TermParser
import qualified Unison.Test.Typechecker as Typechecker

test :: Test ()
test = tests [
  Lexer.test,
  TermParser.test,
  Typechecker.test,
  FileParser.test,
  DataDeclaration.test
 ]

main :: IO ()
main = do
  args <- getArgs
  mapM_ (`hSetEncoding` utf8) [stdout, stdin, stderr]
  runOnly (case args of [] -> ""; [prefix] -> prefix) test