// Copyright (c) 2026-present Douglas Hoard
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package bytecode

import (
	"fmt"
	"testing"
)

func TestOpcodeValues(t *testing.T) {
	fmt.Printf("OpNOP = %d\n", OpNOP)
	fmt.Printf("OpCONST_INT = %d\n", OpCONST_INT)
	fmt.Printf("OpCONST_STRING = %d\n", OpCONST_STRING)
	fmt.Printf("OpCONST_NULL = %d\n", OpCONST_NULL)
	fmt.Printf("OpLOAD_LOCAL = %d\n", OpLOAD_LOCAL)
	fmt.Printf("OpSTORE_LOCAL = %d\n", OpSTORE_LOCAL)
	fmt.Printf("OpPOP = %d\n", OpPOP)
	fmt.Printf("OpADD_INT = %d\n", OpADD_INT)
	fmt.Printf("OpCALL = %d\n", OpCALL)
	fmt.Printf("OpCALL_NATIVE = %d\n", OpCALL_NATIVE)
	fmt.Printf("OpRETURN = %d\n", OpRETURN)
	fmt.Printf("OpRETURN_VOID = %d\n", OpRETURN_VOID)
	fmt.Printf("OpNEW_LIST = %d\n", OpNEW_LIST)
	fmt.Printf("OpLIST_APPEND = %d\n", OpLIST_APPEND)
	fmt.Printf("OpLIST_GET = %d\n", OpLIST_GET)
	fmt.Printf("OpLIST_LENGTH = %d\n", OpLIST_LENGTH)
	fmt.Printf("OpJUMP = %d\n", OpJUMP)
	fmt.Printf("OpJUMP_IF_FALSE = %d\n", OpJUMP_IF_FALSE)
	fmt.Printf("OpJUMP_IF_TRUE = %d\n", OpJUMP_IF_TRUE)
	fmt.Printf("OpHALT = %d\n", OpHALT)
}
