module Databrary.Web.HTTP
  ( splitHTTP
  , quoteHTTP
  , unquoteHTTP
  , formatHTTPTimestamp
  , parseHTTPTimestamp
  ) where

import Control.Monad (msum)
import qualified Data.ByteString as BS
import qualified Data.ByteString.Char8 as BSC
import Data.Char (isSpace, isControl)
import Data.Time.Format (formatTime, parseTime)
import System.Locale (defaultTimeLocale)

import Databrary.Model.Time

splitHTTP :: BS.ByteString -> [BS.ByteString]
splitHTTP = filter (not . BS.null) . map trim . BSC.split ',' where
  trim = fst . BSC.spanEnd isSpace . BSC.dropWhile isSpace

quoteHTTP :: BS.ByteString -> BS.ByteString
quoteHTTP = BSC.pack . ('"':) . quote . BSC.unpack where
  quote "" = "\""
  quote ('\\':r) = '\\':'\\':quote r
  quote ('"':r) = '\\':'"':quote r
  quote (c:r)
    | isControl c = '\\':c:quote r
    | otherwise = c:quote r

unquoteHTTP :: BS.ByteString -> BS.ByteString
unquoteHTTP s
  | BS.length s >= 2 && BSC.head s == '"' && BSC.last s == '"' =
    BSC.pack $ unquote $ BSC.unpack $ BS.tail $ BS.init s
  | otherwise = s where
    unquote ('\\':c:r) = c:unquote r
    unquote (c:r) = c:unquote r
    unquote [] = []

dateFmts :: [String]
-- rfc1123Date, rfc850Date, asctimeDate
dateFmts = ["%a, %d %b %Y %T GMT", "%A, %d-%b-%y %T GMT", "%a %b %e %T %Y"]

defaultDateFmt :: String
defaultDateFmt = head dateFmts

formatHTTPTimestamp :: Timestamp -> BS.ByteString
-- httpTimestamp = formatHTTPDate . epochTimeToHTTPDate . CTime . round . utcTimeToPOSIXSeconds
formatHTTPTimestamp = BSC.pack . formatTime defaultTimeLocale defaultDateFmt

parseHTTPTimestamp :: BS.ByteString -> Maybe Timestamp
parseHTTPTimestamp b = msum $ map (\f -> parseTime defaultTimeLocale f s) dateFmts where s = BSC.unpack b
