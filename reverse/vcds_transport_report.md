# VCDS 26.3 Transport Layer & Adapter Protocol Analysis Report

**Target Program:** VCDS_unpacked.exe
**Base Address:** 140000000
**Analysis Date:** Wed Sep 23 00:21:21 EEST 2026

## 1. Key Protocol & Hardware Strings with XREFs

### String `"\"Cannot open the Clipboard\""` at `14019c128`
- **XREF from:** `14011d666` in Function **`FUN_14011d624`** (`14011d624`)

```c
// Function: FUN_14011d624 @ 14011d624

undefined8 FUN_14011d624(longlong param_1)

{
  int iVar1;
  undefined8 uVar2;
  longlong lVar3;
  
  iVar1 = (*DAT_14018c740)(*(undefined8 *)(param_1 + 0x3b0));
  uVar2 = 0;
  if (iVar1 == 0) {
    iVar1 = (*DAT_14018cd28)(0);
    if (iVar1 == 0) {
      AfxMessageBox("Cannot open the Clipboard",0,0);
      uVar2 = 0xfffffffe;
    }
    else {
      iVar1 = (*DAT_14018cb68)();
      if (iVar1 == 0) {
        AfxMessageBox("Cannot empty the Clipboard",0,0);
        uVar2 = 0xfffffffd;
      }
      else {
        lVar3 = (*DAT_14018cb70)(1);
        if (lVar3 == 0) {
          AfxMessageBox("Unable to set Clipboard data",0,0);
          uVar2 = 0xfffffffc;
        }
        (*DAT_14018cd60)();
      }
    }
  }
  else {
    AfxMessageBox("Can\'t unlock memory",0,0);
    uVar2 = 0xffffffff;
  }
  return uVar2;
}


```

- **XREF from:** `140104bba` in Function **`FUN_140104a14`** (`140104a14`)

```c
// Function: FUN_140104a14 @ 140104a14

void FUN_140104a14(CWnd *param_1)

{
  int *piVar1;
  int iVar2;
  longlong *plVar3;
  longlong lVar4;
  CWnd *pCVar5;
  char *pcVar6;
  size_t sVar7;
  char *local_res10;
  
  plVar3 = (longlong *)FUN_14013a630();
  if (plVar3 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  lVar4 = (**(code **)(*plVar3 + 0x18))(plVar3);
  local_res10 = (char *)(lVar4 + 0x18);
  pCVar5 = CWnd::GetDlgItem(param_1,0x5f7);
  FUN_140140b14(pCVar5,&local_res10);
  pcVar6 = strstr(local_res10,*(char **)(param_1 + 0x1a0));
  if (pcVar6 == (char *)0x0) {
    iVar2 = (*DAT_14018cd58)(1);
    if (iVar2 == 0) {
      LOCK();
      piVar1 = (int *)(local_res10 + -8);
      iVar2 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (0 < iVar2 + -1) {
        return;
      }
      (**(code **)(**(longlong **)(local_res10 + -0x18) + 8))();
      return;
    }
    iVar2 = (*DAT_14018cd28)(0);
    if (iVar2 == 0) {
      AfxMessageBox("Cannot open the Clipboard",0,0);
      LOCK();
      piVar1 = (int *)(local_res10 + -8);
      iVar2 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (0 < iVar2 + -1) {
        return;
      }
      (**(code **)(**(longlong **)(local_res10 + -0x18) + 8))();
      return;
    }
    lVar4 = (*DAT_14018cd40)(1);
    if ((lVar4 != 0) && (pcVar6 = (char *)(*DAT_14018c758)(lVar4), pcVar6 != (char *)0x0)) {
      sVar7 = strlen(pcVar6);
      FUN_140001a34(&local_res10,pcVar6,sVar7 & 0xffffffff);
      (*DAT_14018c740)(lVar4);
      FUN_140143988(param_1 + 0xe8,local_res10);
    }
    (*DAT_14018cd60)();
  }
  else {
    FUN_140140b14(param_1 + 0xe8,&local_res10);
    pcVar6 = (char *)FUN_14011d5c4(&DAT_140701ec0,*(int *)(local_res10 + -0x10) + 1);
    if (pcVar6 == (char *)0x0) {
      LOCK();
      piVar1 = (int *)(local_res10 + -8);
      iVar2 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (0 < iVar2 + -1) {
        return;
      }
      (**(code **)(**(longlong **)(local_res10 + -0x18) + 8))();
      return;
    }
    strncpy(pcVar6,local_res10,(longlong)*(int *)(local_res10 + -0x10));
    FUN_14011d624(&DAT_140701ec0);
    lVar4 = FUN_14013a61c(0x368);
    if (lVar4 != 0) {
      FUN_1400018a8(&local_res10,lVar4,0x368);
    }
    pCVar5 = CWnd::GetDlgItem(param_1,0x5f7);
    FUN_140143988(pCVar5,local_res10);
    pCVar5 = CWnd::GetDlgItem(param_1,0x5f7);
    CWnd::EnableWindow(pCVar5,0);
    (*DAT_14018ccc8)(*(undefined8 *)(param_1 + 0x40),2,2000);
  }
  LOCK();
  piVar1 = (int *)(local_res10 + -8);
  iVar2 = *piVar1;
  *piVar1 = *piVar1 + -1;
  UNLOCK();
  if (iVar2 + -1 < 1) {
    (**(code **)(**(longlong **)(local_res10 + -0x18) + 8))();
  }
  return;
}


```


### String `"\"TP 2.0\""` at `14019c650`
- **XREF from:** `14001d6c5` in Function **`FUN_14001d4d4`** (`14001d4d4`)

```c
// Function: FUN_14001d4d4 @ 14001d4d4

void FUN_14001d4d4(longlong param_1)

{
  int *piVar1;
  byte bVar2;
  undefined1 uVar3;
  undefined1 uVar4;
  undefined1 uVar5;
  char cVar6;
  int iVar7;
  longlong *plVar8;
  undefined8 *puVar9;
  longlong lVar10;
  size_t sVar11;
  char *pcVar12;
  char *pcVar13;
  int iVar14;
  longlong lVar15;
  longlong lVar16;
  longlong local_res18;
  char *local_res20;
  
  plVar8 = (longlong *)FUN_14013a630();
  if (plVar8 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  local_res18 = (**(code **)(*plVar8 + 0x18))(plVar8);
  local_res18 = local_res18 + 0x18;
  plVar8 = (longlong *)FUN_14013a630();
  if (plVar8 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  puVar9 = (undefined8 *)(**(code **)(*plVar8 + 0x18))(plVar8);
  pcVar13 = (char *)(puVar9 + 3);
  DAT_1405a5100 = 3;
  DAT_1405a5101 = 0x22;
  DAT_1405a5102 = 0xf1;
  DAT_1405a5103 = 0x81;
  local_res20 = pcVar13;
  cVar6 = FUN_140020e50(param_1);
  if (cVar6 == '\0') {
    LOCK();
    piVar1 = (int *)(puVar9 + 2);
    iVar7 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar7 + -1 < 1) {
      (**(code **)(*(longlong *)*puVar9 + 8))();
    }
    LOCK();
    piVar1 = (int *)(local_res18 + -8);
    iVar7 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar7 + -1 < 1) {
      (**(code **)(**(longlong **)(local_res18 + -0x18) + 8))();
    }
  }
  else {
    iVar7 = 0;
    if (0 < DAT_1405a7328) {
      pcVar12 = &DAT_1405a734c;
      do {
        if (*pcVar12 == 'b') break;
        iVar7 = iVar7 + 1;
        pcVar12 = pcVar12 + 0x40c;
      } while (iVar7 < DAT_1405a7328);
    }
    if (iVar7 == DAT_1405a7328) {
      *(undefined4 *)(param_1 + 0x1a4) = 0xffffffff;
    }
    else {
      iVar14 = 0;
      lVar15 = (longlong)iVar7;
      if (5 < (int)(&DAT_1405a7348)[lVar15 * 0x103]) {
        lVar16 = 0;
        iVar7 = 6;
        do {
          if (0x401 < iVar7) break;
          lVar10 = lVar15 * 0x40c + lVar16;
          bVar2 = (&DAT_1405a734f)[lVar10];
          uVar3 = (&DAT_1405a7350)[lVar10];
          uVar4 = (&DAT_1405a7351)[lVar10];
          uVar5 = (&DAT_1405a7352)[lVar10];
          if (bVar2 < 0x25) {
            if (bVar2 == 0x24) {
              sVar11 = strlen("FlexRay Trsp");
              FUN_140001a34(&local_res18,"FlexRay Trsp",sVar11 & 0xffffffff);
            }
            else if (bVar2 < 0xd) {
              if (bVar2 == 0xc) {
                sVar11 = strlen("OSEK-Com");
                FUN_140001a34(&local_res18,"OSEK-Com",sVar11 & 0xffffffff);
              }
              else if (bVar2 < 7) {
                if (bVar2 == 6) {
                  sVar11 = strlen("TP 1.6");
                  FUN_140001a34(&local_res18,"TP 1.6",sVar11 & 0xffffffff);
                }
                else if (bVar2 == 0) {
                  sVar11 = strlen("Theft prot.");
                  FUN_140001a34(&local_res18,"Theft prot.",sVar11 & 0xffffffff);
                }
                else if (bVar2 == 1) {
                  sVar11 = strlen("BAP");
                  FUN_140001a34(&local_res18,&DAT_14019c63c,sVar11 & 0xffffffff);
                }
                else if (bVar2 == 2) {
                  sVar11 = strlen("UDS");
                  FUN_140001a34(&local_res18,&DAT_14019c640,sVar11 & 0xffffffff);
                }
                else if (bVar2 == 3) {
                  sVar11 = strlen("Diag");
                  FUN_140001a34(&local_res18,&DAT_14019c644,sVar11 & 0xffffffff);
                }
                else if (bVar2 == 4) {
                  sVar11 = strlen("CAN");
                  FUN_140001a34(&local_res18,&DAT_14019c64c,sVar11 & 0xffffffff);
                }
                else {
                  if (bVar2 != 5) goto LAB_14001db6e;
                  sVar11 = strlen("TP 2.0");
                  FUN_140001a34(&local_res18,"TP 2.0",sVar11 & 0xffffffff);
                }
              }
              else if (bVar2 == 7) {
                sVar11 = strlen("ISO15765");
                FUN_140001a34(&local_res18,"ISO15765",sVar11 & 0xffffffff);
              }
              else if (bVar2 == 8) {
                sVar11 = strlen("KWP2000");
                FUN_140001a34(&local_res18,"KWP2000",sVar11 & 0xffffffff);
              }
              else if (bVar2 == 9) {
                sVar11 = strlen("OSEK-OS");
                FUN_140001a34(&local_res18,"OSEK-OS",sVar11 & 0xffffffff);
              }
              else if (bVar2 == 10) {
                sVar11 = strlen("OSEK Net");
                FUN_140001a34(&local_res18,"OSEK Net",sVar11 & 0xffffffff);
              }
              else {
                if (bVar2 != 0xb) goto LAB_14001db6e;
                sVar11 = strlen("HiSpd Net");
                FUN_140001a34(&local_res18,"HiSpd Net",sVar11 & 0xffffffff);
              }
            }
            else if (bVar2 < 0x17) {
              if (bVar2 == 0x16) {
                sVar11 = strlen("Bootloader/Flasher");
                FUN_140001a34(&local_res18,"Bootloader/Flasher",sVar11 & 0xffffffff);
              }
              else if (bVar2 == 0xd) {
                sVar11 = strlen("LIN 1.3");
                FUN_140001a34(&local_res18,"LIN 1.3",sVar11 & 0xffffffff);
              }
              else if (bVar2 == 0xe) {
                sVar11 = strlen("LIN 2.0");
                FUN_140001a34(&local_res18,"LIN 2.0",sVar11 & 0xffffffff);
              }
              else if (bVar2 == 0xf) {
                sVar11 = strlen("I/O lib.");
                FUN_140001a34(&local_res18,"I/O lib.",sVar11 & 0xffffffff);
              }
              else if (bVar2 == 0x10) {
                sVar11 = strlen("EEProm lib.");
                FUN_140001a34(&local_res18,"EEProm lib.",sVar11 & 0xffffffff);
              }
              else if (bVar2 == 0x11) {
                sVar11 = strlen("MOST Net");
                FUN_140001a34(&local_res18,"MOST Net",sVar11 & 0xffffffff);
              }
              else {
                if (bVar2 != 0x15) goto LAB_14001db6e;
                sVar11 = strlen("UDS Std Diag");
                FUN_140001a34(&local_res18,"UDS Std Diag",sVar11 & 0xffffffff);
              }
            }
            else if (bVar2 == 0x18) {
              sVar11 = strlen("AUTOSAR-ISO TP");
              FUN_140001a34(&local_res18,"AUTOSAR-ISO TP",sVar11 & 0xffffffff);
            }
            else if (bVar2 == 0x1b) {
              sVar11 = strlen("MOST Firmware");
              FUN_140001a34(&local_res18,"MOST Firmware",sVar11 & 0xffffffff);
            }
            else if (bVar2 == 0x1c) {
              sVar11 = strlen("INIC Config Str");
              FUN_140001a34(&local_res18,"INIC Config Str",sVar11 & 0xffffffff);
            }
            else if (bVar2 == 0x1e) {
              sVar11 = strlen("Generic Net Mgt");
              FUN_140001a34(&local_res18,"Generic Net Mgt",sVar11 & 0xffffffff);
            }
            else {
              if (bVar2 != 0x20) goto LAB_14001db6e;
              sVar11 = strlen("FlexRay Net Mgt");
              FUN_140001a34(&local_res18,"FlexRay Net Mgt",sVar11 & 0xffffffff);
            }
          }
          else if (bVar2 == 0x33) {
            sVar11 = strlen("Prot Data Unit Router");
            FUN_140001a34(&local_res18,"Prot Data Unit Router",sVar11 & 0xffffffff);
          }
          else if (bVar2 == 0x3d) {
            sVar11 = strlen("FlexRay Interf");
            FUN_140001a34(&local_res18,"FlexRay Interf",sVar11 & 0xffffffff);
          }
          else if (bVar2 == 0x51) {
            sVar11 = strlen("FlexRay Drv");
            FUN_140001a34(&local_res18,"FlexRay Drv",sVar11 & 0xffffffff);
          }
          else {
LAB_14001db6e:
            lVar10 = FUN_14013a61c(0x15e);
            if (lVar10 != 0) {
              FUN_1400018a8(&local_res18,lVar10,0x15e);
            }
          }
          FUN_140003cf0(&local_res20,"%s: %02X.%02X.%02X",local_res18,uVar3,uVar4,uVar5);
          pcVar13 = local_res20;
          pcVar12 = strstr(local_res20,"FF.FF.FF");
          if (pcVar12 == (char *)0x0) {
            lVar10 = (longlong)iVar14;
            iVar14 = iVar14 + 1;
            FID_conflict_operator_(param_1 + 0x240 + lVar10 * 8,&local_res20);
          }
          iVar7 = iVar7 + 4;
          lVar16 = lVar16 + 4;
        } while (iVar7 <= (int)(&DAT_1405a7348)[lVar15 * 0x103]);
      }
      *(int *)(param_1 + 0x1a4) = iVar14 + -1;
      *(undefined4 *)(param_1 + 0x1a0) = 0;
      FUN_140019d84(param_1);
    }
    LOCK();
    piVar1 = (int *)(pcVar13 + -8);
    iVar7 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar7 + -1 < 1) {
      (**(code **)(**(longlong **)(pcVar13 + -0x18) + 8))();
    }
    LOCK();
    piVar1 = (int *)(local_res18 + -8);
    iVar7 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar7 + -1 < 1) {
      (**(code **)(**(longlong **)(local_res18 + -0x18) + 8))();
    }
  }
  return;
}


```

- **XREF from:** `14001d6d4` in Function **`FUN_14001d4d4`** (`14001d4d4`)

### String `"\"KWP2000\""` at `14019c670`
- **XREF from:** `14001d87a` in Function **`FUN_14001d4d4`** (`14001d4d4`)
- **XREF from:** `14001d889` in Function **`FUN_14001d4d4`** (`14001d4d4`)

### String `"\"Can't create Control File MB-PIPE.TXT\""` at `14019d0b8`
- **XREF from:** `1400592d1` in Function **`FUN_14005911c`** (`14005911c`)

```c
// Function: FUN_14005911c @ 14005911c

void FUN_14005911c(CWnd *param_1)

{
  int *piVar1;
  LPCSTR lpCaption;
  int iVar2;
  longlong *plVar3;
  longlong lVar4;
  size_t sVar5;
  FILE *_File;
  CWnd *this;
  LPCSTR local_res10;
  LPCSTR local_res18 [2];
  undefined1 local_208 [512];
  
  plVar3 = (longlong *)FUN_14013a630();
  if (plVar3 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  lVar4 = (**(code **)(*plVar3 + 0x18))(plVar3);
  local_res18[0] = (LPCSTR)(lVar4 + 0x18);
  plVar3 = (longlong *)FUN_14013a630();
  if (plVar3 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  lVar4 = (**(code **)(*plVar3 + 0x18))(plVar3);
  local_res10 = (LPCSTR)(lVar4 + 0x18);
  sVar5 = strlen("VCDS");
  FUN_140001a34(&local_res10,&DAT_14019aec0,sVar5 & 0xffffffff);
  sVar5 = strlen(": Error");
  FUN_14000d230(&local_res10,": Error",sVar5 & 0xffffffff);
  _File = (FILE *)FUN_140156994("VCScope.txt",&DAT_14019d090);
  if (_File == (FILE *)0x0) {
    FUN_140156be0(local_208,"VCSCOPE.EXE");
  }
  else {
    FUN_140065420(_File,local_208,0);
    fclose(_File);
  }
  iVar2 = FUN_1400f2e8c("MB-PIPE.TXT",local_208);
  lpCaption = local_res10;
  if (iVar2 == -2) {
    DAT_140631f58 = 0xffffffff;
    DAT_140630e28 = 0;
    FUN_140003cf0(local_res18,"Can\'t spawn %s",local_208);
    FID_conflict_MessageBoxA((HWND)param_1,local_res18[0],lpCaption,0x10);
  }
  else if (iVar2 == -1) {
    DAT_140631f58 = 0xffffffff;
    DAT_140630e28 = 0;
    FID_conflict_MessageBoxA
              ((HWND)param_1,"Can\'t create Control File MB-PIPE.TXT",local_res10,0x10);
  }
  else if (iVar2 == 0) {
    DAT_140631f58 = 1;
    (*DAT_14018ccc8)(*(undefined8 *)(param_1 + 0x40),4,1000);
    this = CWnd::GetDlgItem(param_1,0x4c3);
    CWnd::EnableWindow(this,0);
  }
  else {
    DAT_140631f58 = 0xffffffff;
    DAT_140630e28 = 0;
    FID_conflict_MessageBoxA((HWND)param_1,"Unexpected Error in BlockDlg::OnGraph",local_res10,0x10)
    ;
  }
  LOCK();
  piVar1 = (int *)(lpCaption + -8);
  iVar2 = *piVar1;
  *piVar1 = *piVar1 + -1;
  UNLOCK();
  if (iVar2 + -1 < 1) {
    (**(code **)(**(longlong **)(lpCaption + -0x18) + 8))();
  }
  LOCK();
  piVar1 = (int *)(local_res18[0] + -8);
  iVar2 = *piVar1;
  *piVar1 = *piVar1 + -1;
  UNLOCK();
  if (iVar2 + -1 < 1) {
    (**(code **)(**(longlong **)(local_res18[0] + -0x18) + 8))();
  }
  return;
}


```

- **XREF from:** `140059938` in Function **`FUN_1400597c8`** (`1400597c8`)

```c
// Function: FUN_1400597c8 @ 1400597c8

void FUN_1400597c8(CWnd *param_1)

{
  int *piVar1;
  LPCSTR pCVar2;
  int iVar3;
  longlong *plVar4;
  longlong lVar5;
  size_t sVar6;
  CWnd *pCVar7;
  LPCSTR local_res10 [3];
  
  plVar4 = (longlong *)FUN_14013a630();
  if (plVar4 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  lVar5 = (**(code **)(*plVar4 + 0x18))(plVar4);
  local_res10[0] = (LPCSTR)(lVar5 + 0x18);
  sVar6 = strlen("VCDS");
  FUN_140001a34(local_res10,&DAT_14019aec0,sVar6 & 0xffffffff);
  sVar6 = strlen(": Error");
  FUN_14000d230(local_res10,": Error",sVar6 & 0xffffffff);
  DAT_140631e58 = 1;
  iVar3 = FUN_1400f2e8c("MB-PIPE.TXT","TDIGraph.EXE");
  pCVar2 = local_res10[0];
  if (iVar3 == -2) {
    DAT_140631f58 = 0xffffffff;
    FID_conflict_MessageBoxA((HWND)param_1,"Can\'t spawn TDIGraph.EXE",local_res10[0],0x10);
  }
  else if (iVar3 == -1) {
    DAT_140631f58 = 0xffffffff;
    FID_conflict_MessageBoxA
              ((HWND)param_1,"Can\'t create Control File MB-PIPE.TXT",local_res10[0],0x10);
  }
  else if (iVar3 == 0) {
    DAT_140631f58 = 1;
    pCVar7 = CWnd::GetDlgItem(param_1,0x4c3);
    CWnd::EnableWindow(pCVar7,0);
    pCVar7 = CWnd::GetDlgItem(param_1,0x4c4);
    CWnd::EnableWindow(pCVar7,0);
    pCVar7 = CWnd::GetDlgItem(param_1,0x495);
    CWnd::EnableWindow(pCVar7,0);
    pCVar7 = CWnd::GetDlgItem(param_1,0x4c2);
    CWnd::EnableWindow(pCVar7,0);
    pCVar7 = CWnd::GetDlgItem(param_1,0x46f);
    CWnd::EnableWindow(pCVar7,0);
  }
  else {
    DAT_140631f58 = 0xffffffff;
    FID_conflict_MessageBoxA
              ((HWND)param_1,"Unexpected Error in BlockDlg::OnGraph2",local_res10[0],0x10);
  }
  LOCK();
  piVar1 = (int *)(pCVar2 + -8);
  iVar3 = *piVar1;
  *piVar1 = *piVar1 + -1;
  UNLOCK();
  if (iVar3 + -1 < 1) {
    (**(code **)(**(longlong **)(pCVar2 + -0x18) + 8))();
  }
  return;
}


```


### String `"\"Can't spawn %s\""` at `14019d0e0`
- **XREF from:** `1400592f6` in Function **`FUN_14005911c`** (`14005911c`)

### String `"\"Group UDS \""` at `14019d150`
- **XREF from:** `140027372` in Function **`FUN_140027340`** (`140027340`)

```c
// Function: FUN_140027340 @ 140027340

void FUN_140027340(CWnd *param_1)

{
  longlong lVar1;
  
  CWnd::UpdateData(param_1,1);
  *(undefined4 *)(param_1 + 0xa8c) = 0;
  if (DAT_1405a7338 != 0) {
    FUN_1400a1378(1);
    FUN_1400a1318();
    FUN_140156b40(&DAT_1405a5df0,"Group UDS ");
    if (*(int *)(param_1 + 0xb94) == 0) {
      FUN_140156b40(&DAT_1405a5df0,&DAT_14019d15c);
    }
    FUN_140156b40(&DAT_1405a5df0,"selected");
  }
  CWnd::ShowWindow(param_1 + 0xba0,*(int *)(param_1 + 0xb94));
  param_1 = param_1 + 0xb24;
  lVar1 = 0xc;
  do {
    param_1[-0xc] = (CWnd)0x0;
    *param_1 = (CWnd)0x0;
    param_1 = param_1 + 1;
    lVar1 = lVar1 + -1;
  } while (lVar1 != 0);
  return;
}


```


### String `"\"Group UDS split %d\""` at `14019d1b0`
- **XREF from:** `1400292fd` in Function **`FUN_1400292a4`** (`1400292a4`)

```c
// Function: FUN_1400292a4 @ 1400292a4

void FUN_1400292a4(longlong param_1)

{
  int iVar1;
  
  iVar1 = (*DAT_14018ccf8)(*(undefined8 *)(param_1 + 0xbe0),0x147,0,0);
  if (iVar1 == 0) {
    *(undefined4 *)(param_1 + 0xb30) = 0xd;
  }
  else {
    *(int *)(param_1 + 0xb30) = iVar1 + 6;
  }
  if (DAT_1405a7338 != 0) {
    FUN_1400a1378(1);
    FUN_1400a1318();
    sprintf(&DAT_1405a59f0,"Group UDS split %d",(ulonglong)*(uint *)(param_1 + 0xb30));
    FUN_140156b40(&DAT_1405a5df0,&DAT_1405a59f0);
  }
  return;
}


```


### String `"\"OVERRIDDING with autoscan user chassis %s\""` at `14019de68`
- *(No direct XREFs found)*

### String `"\"CRFT_LDWS\""` at `14019e3f0`
- *(No direct XREFs found)*

### String `"\"CRFT_ESP\""` at `14019e418`
- *(No direct XREFs found)*

### String `"\"CRFT_KOMBI\""` at `14019e428`
- *(No direct XREFs found)*

### String `"\"CRFT_FAHRT\""` at `14019e438`
- *(No direct XREFs found)*

### String `"\"CRFT_TPMS\""` at `14019e448`
- *(No direct XREFs found)*

### String `"\"CRFT_KLIMA\""` at `14019e478`
- *(No direct XREFs found)*

### String `"\"CRFT_STH\""` at `14019e4a8`
- *(No direct XREFs found)*

### String `"\"CRFT_RAD\""` at `14019e4b8`
- *(No direct XREFs found)*

### String `"\"CRFT_NAV\""` at `14019e4c8`
- *(No direct XREFs found)*

### String `"\"CRFT_EPH\""` at `14019e510`
- *(No direct XREFs found)*

### String `"\"CRFT_TRL\""` at `14019e520`
- *(No direct XREFs found)*

### String `"\"CRFT_PSM\""` at `14019e540`
- *(No direct XREFs found)*

### String `"\"CRFT_STL\""` at `14019e560`
- *(No direct XREFs found)*

### String `"\"CRFT_STR\""` at `14019e570`
- *(No direct XREFs found)*

### String `"\"Scans\""` at `14019ecb0`
- **XREF from non-function address:** `1400404e0`

### String `"\"Autoscan-ClearAll\""` at `14019f0b8`
- *(No direct XREFs found)*

### String `"\"myautoscan.txt\""` at `14019f100`
- **XREF from:** `140049d78` in Function **`FUN_140049d74`** (`140049d74`)

```c
// Function: FUN_140049d74 @ 140049d74

void FUN_140049d74(void)

{
  (*DAT_14018c918)(0,&DAT_14019f11c,"notepad.exe","myautoscan.txt",0,1);
  return;
}


```

- **XREF from:** `14004c0a1` in Function **`FUN_14004bdf4`** (`14004bdf4`)

```c
// Function: FUN_14004bdf4 @ 14004bdf4

int FUN_14004bdf4(HWND param_1,longlong *param_2)

{
  int *piVar1;
  HWND pHVar2;
  int iVar3;
  bool bVar4;
  int iVar5;
  undefined4 uVar6;
  longlong *plVar7;
  longlong lVar8;
  undefined8 *puVar9;
  FILE *_File;
  FILE *_File_00;
  size_t sVar10;
  char *pcVar11;
  undefined8 uVar12;
  ulonglong uVar13;
  LPCSTR lpText;
  char *pcVar14;
  uint uVar15;
  ulonglong uVar17;
  ulonglong uVar18;
  LPCSTR local_res20;
  char *local_458;
  longlong local_450;
  undefined8 local_448;
  char local_438 [1024];
  ulonglong uVar16;
  
  local_448 = 0xfffffffffffffffe;
  plVar7 = (longlong *)FUN_14013a630();
  _File = (FILE *)0x0;
  if (plVar7 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  lVar8 = (**(code **)(*plVar7 + 0x18))(plVar7);
  lpText = (LPCSTR)(lVar8 + 0x18);
  local_res20 = lpText;
  plVar7 = (longlong *)FUN_14013a630();
  if (plVar7 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  puVar9 = (undefined8 *)(**(code **)(*plVar7 + 0x18))(plVar7);
  pcVar14 = (char *)(puVar9 + 3);
  param_1[0x1793a].unused = 0;
  local_458 = pcVar14;
  (*DAT_14018ccf8)(*(undefined8 *)(param_1 + 0x13e),0x14b,0,0);
  bVar4 = 1 < *(int *)(*param_2 + -0x10);
  if (bVar4) {
    FUN_140017348(param_2);
  }
  if ((*(char *)((longlong)&param_1[0x3a].unused + 1) == '\0') &&
     (_File = (FILE *)FUN_140156994("autoscan.txt",&DAT_14019d090), _File == (FILE *)0x0)) {
    lVar8 = FUN_14013a61c(0x1cd);
    if (lVar8 != 0) {
      FUN_1400018a8(&local_res20,lVar8);
      lpText = local_res20;
    }
    FID_conflict_MessageBoxA(param_1,lpText,(LPCSTR)0x0,0x10);
    lVar8 = FUN_14013a61c(0x1ce);
    if (lVar8 != 0) {
      FUN_1400018a8(&local_res20,lVar8,0x1ce);
      lpText = local_res20;
    }
    (*DAT_14018ccf8)(*(undefined8 *)(param_1 + 0x13e),0x143,0,lpText);
    LOCK();
    piVar1 = (int *)(puVar9 + 2);
    iVar5 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar5 + -1 < 1) {
      (**(code **)(*(longlong *)*puVar9 + 8))();
    }
    LOCK();
    piVar1 = (int *)(local_res20 + -8);
    iVar5 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar5 + -1 < 1) {
      (**(code **)(**(longlong **)(local_res20 + -0x18) + 8))();
    }
    lVar8 = *param_2;
    LOCK();
    piVar1 = (int *)(lVar8 + -8);
    iVar5 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar5 + -1 < 1) {
      (**(code **)(**(longlong **)(lVar8 + -0x18) + 8))();
    }
    return 0;
  }
  if (!bVar4) {
    lVar8 = FUN_14013a61c(0x15d);
    if (lVar8 != 0) {
      FUN_1400018a8(&local_res20,lVar8,0x15d);
      lpText = local_res20;
    }
    (*DAT_14018ccf8)(*(undefined8 *)(param_1 + 0x13e),0x143,0,lpText);
    if ((char)param_1[0x18a].unused != '\0') {
      lVar8 = FUN_14013a61c(0x36b);
      if (lVar8 != 0) {
        FUN_1400018a8(&local_res20,lVar8,0x36b);
        lpText = local_res20;
      }
      (*DAT_14018ccf8)(*(undefined8 *)(param_1 + 0x13e),0x143,0,lpText);
    }
  }
  if ((*(char *)((longlong)&param_1[0x3a].unused + 1) == '\0') &&
     (_File_00 = (FILE *)FUN_140156994("myautoscan.txt",&DAT_14019d090), _File_00 != (FILE *)0x0)) {
    iVar5 = FUN_140065420(_File_00,local_438,0x400);
    while (iVar5 != -1) {
      if (bVar4) {
        sVar10 = strlen(local_438);
        FUN_140001a34(&local_res20,local_438,sVar10 & 0xffffffff);
        FUN_140017348(&local_res20);
        pcVar11 = strstr((char *)*param_2,*(char **)(param_1 + 0x1796a));
        lpText = local_res20;
        if (pcVar11 == (char *)0x0) {
          pcVar11 = strstr(local_res20,(char *)*param_2);
          if (pcVar11 != (char *)0x0) goto LAB_14004c1bb;
        }
        else {
          iVar5 = 1;
          do {
            pHVar2 = param_1 + (longlong)iVar5 * 4 + 0x1793d;
            if (pHVar2 == (HWND)0x0) {
              uVar6 = 0;
            }
            else {
              sVar10 = strlen((char *)pHVar2);
              uVar6 = (undefined4)sVar10;
            }
            FUN_140001a34(&local_458,pHVar2,uVar6);
            FUN_140017348(&local_458);
            pcVar14 = local_458;
            pcVar11 = strstr(lpText,local_458);
          } while ((pcVar11 == (char *)0x0) && (iVar5 = iVar5 + 1, iVar5 < 5));
          if (iVar5 == 5) {
            FUN_1400409ec(param_1,local_438);
          }
        }
      }
      else {
LAB_14004c1bb:
        FUN_1400409ec(param_1,local_438);
      }
      iVar5 = FUN_140065420(_File_00,local_438,0x400);
    }
    fclose(_File_00);
  }
  uVar18 = 0;
  if (*(char *)((longlong)&param_1[0x3a].unused + 1) == '\0') {
    iVar5 = FUN_140065420(_File,local_438,0x400);
    while (iVar5 != -1) {
      if (bVar4) {
        if (local_438[0] != ';') {
          sVar10 = strlen(local_438);
          FUN_140001a34(&local_res20,local_438,sVar10 & 0xffffffff);
          FUN_140017348(&local_res20);
          iVar5 = *(int *)(local_res20 + -0x10);
          uVar13 = uVar18;
          uVar16 = uVar18;
          uVar17 = uVar18;
          if (0 < iVar5) {
            do {
              if (((longlong)uVar13 < 0) || (iVar5 < (int)uVar16)) {
                    /* WARNING: Subroutine does not return */
                FUN_140001000(0x80070057);
              }
              if (local_res20[uVar13] == ',') {
                uVar17 = (ulonglong)((int)uVar17 + 1);
              }
              if ((int)uVar17 == 2) break;
              uVar15 = (int)uVar16 + 1;
              uVar16 = (ulonglong)uVar15;
              uVar13 = uVar13 + 1;
            } while ((int)uVar15 < iVar5);
          }
          uVar12 = Left(&local_res20,&local_450);
          FID_conflict_operator_(&local_res20,uVar12);
          LOCK();
          piVar1 = (int *)(local_450 + -8);
          iVar5 = *piVar1;
          *piVar1 = *piVar1 + -1;
          UNLOCK();
          lpText = local_res20;
          pcVar14 = local_458;
          if (iVar5 + -1 < 1) {
            (**(code **)(**(longlong **)(local_450 + -0x18) + 8))();
            lpText = local_res20;
            pcVar14 = local_458;
          }
        }
        pcVar11 = strstr((char *)*param_2,*(char **)(param_1 + 0x1796a));
        if (pcVar11 == (char *)0x0) {
          pcVar11 = strstr(lpText,(char *)*param_2);
          if (pcVar11 != (char *)0x0) goto LAB_14004c3a9;
        }
        else {
          iVar5 = 1;
          do {
            pHVar2 = param_1 + (longlong)iVar5 * 4 + 0x1793d;
            uVar6 = 0;
            if (pHVar2 != (HWND)0x0) {
              sVar10 = strlen((char *)pHVar2);
              uVar6 = (int)sVar10;
            }
            FUN_140001a34(&local_458,pHVar2,uVar6);
            FUN_140017348(&local_458);
            pcVar14 = local_458;
            pcVar11 = strstr(lpText,local_458);
          } while ((pcVar11 == (char *)0x0) && (iVar5 = iVar5 + 1, iVar5 < 5));
          if (iVar5 == 5) {
            FUN_1400409ec(param_1,local_438);
          }
        }
      }
      else {
LAB_14004c3a9:
        FUN_1400409ec(param_1,local_438);
      }
      iVar5 = FUN_140065420(_File,local_438,0x400);
    }
    fclose(_File);
  }
  *(undefined1 *)((longlong)&param_1[0x1788a].unused + (longlong)param_1[0x1793a].unused) = 0;
  iVar5 = 1;
  lVar8 = 1;
  do {
    param_1[(longlong)param_1[0x1793a].unused * 0x101 + lVar8 + 0x192b].unused = iVar5;
    iVar5 = iVar5 + 1;
    lVar8 = lVar8 + 1;
  } while (iVar5 < 0x100);
  param_1[(longlong)param_1[0x1793a].unused * 0x101 + (longlong)iVar5 + 0x192b].unused = 0;
  FUN_140156be0((longlong)&param_1[(longlong)param_1[0x1793a].unused + 0x196].unused +
                (longlong)param_1[0x1793a].unused + 1,&DAT_14019f1e0);
  lVar8 = FUN_14013a61c(0x381);
  if (lVar8 != 0) {
    FUN_1400018a8(&local_res20,lVar8,0x381);
    lpText = local_res20;
  }
  FUN_140156be0((undefined1 *)
                ((longlong)&param_1[(longlong)param_1[0x1793a].unused * 0x10 + 0x34b].unused + 3),
                lpText);
  sprintf(local_438,"%s - %s",(longlong)param_1 + ((longlong)param_1[0x1793a].unused + 0x145) * 5,
          (undefined1 *)
          ((longlong)&param_1[(longlong)param_1[0x1793a].unused * 0x10 + 0x34b].unused + 3));
  if (bVar4) {
    sVar10 = strlen(local_438);
    FUN_140001a34(&local_res20,local_438,sVar10 & 0xffffffff);
    FUN_140017348(&local_res20);
    pcVar11 = strstr((char *)*param_2,*(char **)(param_1 + 0x1796a));
    if (pcVar11 != (char *)0x0) {
      iVar5 = 1;
      do {
        pHVar2 = param_1 + (longlong)iVar5 * 4 + 0x1793d;
        uVar6 = 0;
        if (pHVar2 != (HWND)0x0) {
          sVar10 = strlen((char *)pHVar2);
          uVar6 = (int)sVar10;
        }
        FUN_140001a34(&local_458,pHVar2,uVar6);
        FUN_140017348(&local_458);
        pcVar14 = local_458;
        pcVar11 = strstr(local_res20,local_458);
      } while ((pcVar11 == (char *)0x0) && (iVar5 = iVar5 + 1, iVar5 < 5));
      if (iVar5 == 5) {
        (*DAT_14018ccf8)(*(undefined8 *)(param_1 + 0x13e),0x143,0,local_438);
      }
      goto LAB_14004c5e6;
    }
    pcVar11 = strstr(local_res20,(char *)*param_2);
    if (pcVar11 == (char *)0x0) goto LAB_14004c5e6;
  }
  (*DAT_14018ccf8)(*(undefined8 *)(param_1 + 0x13e),0x143,0,local_438);
LAB_14004c5e6:
  if (!bVar4) {
    if ((char)param_1[0x18a].unused == '\0') {
      (*DAT_14018ccf8)(*(undefined8 *)(param_1 + 0x13e),0x14e,0,0);
    }
    else {
      (*DAT_14018ccf8)(*(undefined8 *)(param_1 + 0x13e),0x14e,1,0);
    }
  }
  iVar3 = param_1[0x1793a].unused;
  LOCK();
  piVar1 = (int *)(pcVar14 + -8);
  iVar5 = *piVar1;
  *piVar1 = *piVar1 + -1;
  UNLOCK();
  if (iVar5 + -1 < 1) {
    (**(code **)(**(longlong **)(pcVar14 + -0x18) + 8))();
  }
  LOCK();
  piVar1 = (int *)(local_res20 + -8);
  iVar5 = *piVar1;
  *piVar1 = *piVar1 + -1;
  UNLOCK();
  if (iVar5 + -1 < 1) {
    (**(code **)(**(longlong **)(local_res20 + -0x18) + 8))();
  }
  lVar8 = *param_2;
  LOCK();
  piVar1 = (int *)(lVar8 + -8);
  iVar5 = *piVar1;
  *piVar1 = *piVar1 + -1;
  UNLOCK();
  if (iVar5 + -1 < 1) {
    (**(code **)(**(longlong **)(lVar8 + -0x18) + 8))();
  }
  return iVar3;
}


```


### String `"\"\n**********Questions?**********\nPlease visit forums.ross-tech.com\nor email: support@ross-tech.com\""` at `14019f140`
- **XREF from:** `14004b0e6` in Function **`FUN_14004aebc`** (`14004aebc`)

```c
// Function: FUN_14004aebc @ 14004aebc

void FUN_14004aebc(CWnd *param_1)

{
  int *piVar1;
  int iVar2;
  int iVar3;
  undefined4 uVar4;
  undefined4 uVar5;
  longlong *plVar6;
  undefined8 *puVar7;
  longlong lVar8;
  CWnd *pCVar9;
  size_t sVar10;
  undefined8 uVar11;
  undefined8 *local_res10;
  longlong local_res18;
  void *local_res20;
  undefined8 in_stack_ffffffffffffff80;
  undefined4 uVar12;
  undefined1 local_30 [8];
  undefined1 local_28 [16];
  
  uVar12 = (undefined4)((ulonglong)in_stack_ffffffffffffff80 >> 0x20);
  plVar6 = (longlong *)FUN_14013a630();
  if (plVar6 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  local_res18 = (**(code **)(*plVar6 + 0x18))(plVar6);
  local_res18 = local_res18 + 0x18;
  plVar6 = (longlong *)FUN_14013a630();
  if (plVar6 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  puVar7 = (undefined8 *)(**(code **)(*plVar6 + 0x18))(plVar6);
  local_res10 = puVar7 + 3;
  param_1[0x5e580] = (CWnd)0x0;
  if (DAT_140631e18 == 0) {
    local_res20 = operator_new(0x160);
    if (local_res20 == (void *)0x0) {
      DAT_140631e18 = 0;
    }
    else {
      DAT_140631e18 = FUN_14004dc90(local_res20);
    }
    iVar2 = (*DAT_14018ccd8)(0x32);
    iVar3 = (*DAT_14018ccd8)(0x1f);
    if (iVar2 < iVar3) {
      uVar4 = (*DAT_14018ccd8)(0x1f);
    }
    else {
      uVar4 = (*DAT_14018ccd8)(0x32);
    }
    iVar2 = (*DAT_14018ccd8)(0x31);
    iVar3 = (*DAT_14018ccd8)(0x1e);
    if (iVar2 < iVar3) {
      uVar5 = (*DAT_14018ccd8)(0x1e);
    }
    else {
      uVar5 = (*DAT_14018ccd8)(0x31);
    }
    lVar8 = (*DAT_14018ccd0)(0,0x7f04,1,uVar5,uVar4,CONCAT44(uVar12,0x8000));
    if (lVar8 != 0) {
      FUN_14004df40(DAT_140631e18,lVar8,uVar5,uVar4);
    }
    pCVar9 = CWnd::GetDlgItem(param_1,0x517);
    (*DAT_14018cd10)(*(undefined8 *)(pCVar9 + 0x40),local_28);
    lVar8 = FUN_14013a61c(0x8b);
    if (lVar8 != 0) {
      FUN_1400018a8(&local_res18,lVar8,0x8b);
    }
    lVar8 = FUN_14013a61c(0x8c);
    if (lVar8 != 0) {
      FUN_1400018a8(&local_res10,lVar8,0x8c);
    }
    sVar10 = strlen(
                   "\n**********Questions?**********\nPlease visit forums.ross-tech.com\nor email: support@ross-tech.com"
                   );
    FUN_14000d230(&local_res10,
                  "\n**********Questions?**********\nPlease visit forums.ross-tech.com\nor email: support@ross-tech.com"
                  ,sVar10 & 0xffffffff);
    uVar11 = FUN_140001790(local_30,&DAT_14019aa80);
    FUN_14004e3ec(DAT_140631e18,&local_res18,&local_res10,&local_res20,0x4400,0,uVar11,0,0,1,0x14,
                  0x28,0);
    LOCK();
    piVar1 = (int *)(local_res10 + -1);
    iVar2 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar2 + -1 < 1) {
      (**(code **)(*(longlong *)local_res10[-3] + 8))();
    }
    LOCK();
    piVar1 = (int *)(local_res18 + -8);
    iVar2 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar2 + -1 < 1) {
      (**(code **)(**(longlong **)(local_res18 + -0x18) + 8))();
    }
  }
  else {
    LOCK();
    piVar1 = (int *)(puVar7 + 2);
    iVar2 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar2 + -1 < 1) {
      (**(code **)(*(longlong *)*puVar7 + 8))();
    }
    LOCK();
    piVar1 = (int *)(local_res18 + -8);
    iVar2 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar2 + -1 < 1) {
      (**(code **)(**(longlong **)(local_res18 + -0x18) + 8))();
    }
  }
  return;
}


```

- **XREF from:** `14004b0f5` in Function **`FUN_14004aebc`** (`14004aebc`)

### String `"\".\\Scans\\Scan-\""` at `14019f1a8`
- **XREF from:** `14004b313` in Function **`FUN_14004b294`** (`14004b294`)

```c
// Function: FUN_14004b294 @ 14004b294

/* WARNING: Globals starting with '_' overlap smaller symbols at the same address */

undefined8 FUN_14004b294(longlong param_1)

{
  int *piVar1;
  int iVar2;
  longlong *plVar3;
  longlong lVar4;
  size_t sVar5;
  undefined8 *puVar6;
  undefined8 uVar7;
  FILE *_File;
  int iVar8;
  char *local_res10;
  longlong local_res18;
  longlong local_res20;
  undefined1 local_240 [24];
  char local_228 [512];
  
  plVar3 = (longlong *)FUN_14013a630();
  if (plVar3 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  local_res18 = (**(code **)(*plVar3 + 0x18))(plVar3);
  local_res18 = local_res18 + 0x18;
  plVar3 = (longlong *)FUN_14013a630();
  if (plVar3 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  lVar4 = (**(code **)(*plVar3 + 0x18))(plVar3);
  local_res10 = (char *)(lVar4 + 0x18);
  sVar5 = strlen(".\\Scans\\Scan-");
  FUN_140001a34(&local_res18,".\\Scans\\Scan-",sVar5 & 0xffffffff);
  if (*(int *)(DAT_140638220 + -0x10) != 0) {
    FUN_14000d230(&local_res18);
    sVar5 = strlen("-");
    FUN_14000d230(&local_res18,&DAT_14019bfbc,sVar5 & 0xffffffff);
  }
  puVar6 = (undefined8 *)FUN_1400176fc(local_240);
  DAT_1407022b8 = *puVar6;
  _DAT_1407022c0 = puVar6[1];
  uVar7 = FUN_1400177bc(&DAT_1407022b8,&local_res20,"%Y%m%d-%H%M");
  FID_conflict_operator_(&local_res10,uVar7);
  LOCK();
  piVar1 = (int *)(local_res20 + -8);
  iVar2 = *piVar1;
  *piVar1 = *piVar1 + -1;
  UNLOCK();
  if (iVar2 + -1 < 1) {
    (**(code **)(**(longlong **)(local_res20 + -0x18) + 8))();
  }
  FUN_14000d230(&local_res18,local_res10,*(undefined4 *)(local_res10 + -0x10));
  if (0 < DAT_14021b764) {
    FUN_140003cf0(&local_res10,"-%dkm-%dmi",DAT_14021b764,
                  (int)((double)DAT_14021b764 * _DAT_1401c28c8));
    FUN_14000d230(&local_res18,local_res10,*(undefined4 *)(local_res10 + -0x10));
  }
  sVar5 = strlen(".txt");
  FUN_14000d230(&local_res18,&DAT_14019f1c4,sVar5 & 0xffffffff);
  _File = (FILE *)FUN_140156994(local_res18,&DAT_14019af8c);
  if (_File == (FILE *)0x0) {
    LOCK();
    piVar1 = (int *)(local_res10 + -8);
    iVar2 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar2 + -1 < 1) {
      (**(code **)(**(longlong **)(local_res10 + -0x18) + 8))();
    }
    LOCK();
    piVar1 = (int *)(local_res18 + -8);
    iVar2 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar2 + -1 < 1) {
      (**(code **)(**(longlong **)(local_res18 + -0x18) + 8))();
    }
    uVar7 = 0xffffffff;
  }
  else {
    FUN_14011d8d8(&DAT_140701ec0,0,local_228);
    fputs(local_228,_File);
    iVar8 = 0;
    iVar2 = (*DAT_14018ccf8)(*(undefined8 *)(param_1 + 0x300),0x18b,0,0);
    if (0 < iVar2) {
      do {
        FUN_1401440f8(param_1 + 0x2c0,iVar8,&local_res10);
        sVar5 = strlen("\n");
        FUN_14000d230(&local_res10,&DAT_14019af90,sVar5 & 0xffffffff);
        fputs(local_res10,_File);
        iVar8 = iVar8 + 1;
        if (iVar8 == 10000) break;
        iVar2 = (*DAT_14018ccf8)(*(undefined8 *)(param_1 + 0x300),0x18b,0,0);
      } while (iVar8 < iVar2);
    }
    fclose(_File);
    LOCK();
    piVar1 = (int *)(local_res10 + -8);
    iVar2 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar2 + -1 < 1) {
      (**(code **)(**(longlong **)(local_res10 + -0x18) + 8))();
    }
    LOCK();
    piVar1 = (int *)(local_res18 + -8);
    iVar2 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar2 + -1 < 1) {
      (**(code **)(**(longlong **)(local_res18 + -0x18) + 8))();
    }
    uVar7 = 1;
  }
  return uVar7;
}


```

- **XREF from:** `14004b322` in Function **`FUN_14004b294`** (`14004b294`)

### String `"\"autoscan.txt\""` at `14019f1d0`
- **XREF from:** `14004beeb` in Function **`FUN_14004bdf4`** (`14004bdf4`)

### String `"\"http://www.ross-tech.com/misc/uploadform.php?serial=\""` at `14019f1e8`
- **XREF from:** `1400ad864` in Function **`FUN_1400ad824`** (`1400ad824`)

```c
// Function: FUN_1400ad824 @ 1400ad824

void FUN_1400ad824(void)

{
  int *piVar1;
  int iVar2;
  longlong lVar3;
  longlong *plVar4;
  size_t sVar5;
  longlong local_res10 [3];
  
  plVar4 = (longlong *)FUN_14013a630();
  if (plVar4 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  local_res10[0] = (**(code **)(*plVar4 + 0x18))(plVar4);
  local_res10[0] = local_res10[0] + 0x18;
  sVar5 = strlen("http://www.ross-tech.com/misc/uploadform.php?serial=");
  FUN_140001a34(local_res10,"http://www.ross-tech.com/misc/uploadform.php?serial=",
                sVar5 & 0xffffffff);
  FUN_14000d230(local_res10,DAT_140702238,*(undefined4 *)(DAT_140702238 + -0x10));
  lVar3 = local_res10[0];
  (*DAT_14018c918)(0,0,local_res10[0],0,0,1);
  LOCK();
  piVar1 = (int *)(lVar3 + -8);
  iVar2 = *piVar1;
  *piVar1 = *piVar1 + -1;
  UNLOCK();
  if (iVar2 + -1 < 1) {
    (**(code **)(**(longlong **)(lVar3 + -0x18) + 8))();
  }
  return;
}


```

- **XREF from:** `1400ad873` in Function **`FUN_1400ad824`** (`1400ad824`)
- **XREF from:** `14004c916` in Function **`FUN_14004c8b4`** (`14004c8b4`)

```c
// Function: FUN_14004c8b4 @ 14004c8b4

void FUN_14004c8b4(undefined8 param_1,uint param_2)

{
  int *piVar1;
  int iVar2;
  longlong *plVar3;
  longlong lVar4;
  size_t sVar5;
  longlong local_res20;
  undefined8 uVar6;
  
  uVar6 = 0xfffffffffffffffe;
  plVar3 = (longlong *)FUN_14013a630();
  if (plVar3 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  lVar4 = (**(code **)(*plVar3 + 0x18))(plVar3);
  lVar4 = lVar4 + 0x18;
  param_2 = param_2 & 0xfff0;
  local_res20 = lVar4;
  if (param_2 == 0x70) {
    FUN_140046400(param_1);
    sVar5 = strlen("http://www.ross-tech.com/misc/uploadform.php?serial=");
    FUN_140001a34(&local_res20,"http://www.ross-tech.com/misc/uploadform.php?serial=",
                  sVar5 & 0xffffffff);
    FUN_14000d230(&local_res20,DAT_140702238,*(undefined4 *)(DAT_140702238 + -0x10));
    lVar4 = local_res20;
    (*DAT_14018c918)(0,0,local_res20,0,0,1);
  }
  else if (param_2 == 0x20) {
    (*DAT_14018c918)(0,"explore",".\\Scans",0,0,1,uVar6);
  }
  else if (param_2 == 0x30) {
    FUN_14004c9e4(param_1);
  }
  else {
    FUN_14013f3e8(param_1);
  }
  LOCK();
  piVar1 = (int *)(lVar4 + -8);
  iVar2 = *piVar1;
  *piVar1 = *piVar1 + -1;
  UNLOCK();
  if (iVar2 + -1 < 1) {
    (**(code **)(**(longlong **)(lVar4 + -0x18) + 8))();
  }
  return;
}


```

- **XREF from:** `14004c925` in Function **`FUN_14004c8b4`** (`14004c8b4`)
- **XREF from:** `14004cd2a` in Function **`FUN_14004c9e4`** (`14004c9e4`)

```c
// Function: FUN_14004c9e4 @ 14004c9e4

void FUN_14004c9e4(undefined8 param_1)

{
  int *piVar1;
  long lVar2;
  int iVar3;
  longlong *plVar4;
  size_t sVar5;
  longlong lVar6;
  __int64 _Var7;
  undefined8 uVar8;
  FILE *_File;
  longlong lVar9;
  longlong local_res10;
  longlong local_res18;
  longlong local_res20;
  undefined8 in_stack_fffffffffffff508;
  undefined4 uVar10;
  undefined8 *puVar11;
  undefined8 local_ac0;
  undefined8 local_ab8;
  undefined8 local_ab0;
  undefined4 local_aa8;
  undefined2 local_aa4;
  undefined1 local_aa2;
  CFileDialog local_a98 [640];
  char local_818 [2048];
  
  uVar10 = (undefined4)((ulonglong)in_stack_fffffffffffff508 >> 0x20);
  plVar4 = (longlong *)FUN_14013a630();
  if (plVar4 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  local_res18 = (**(code **)(*plVar4 + 0x18))(plVar4);
  local_res18 = local_res18 + 0x18;
  plVar4 = (longlong *)FUN_14013a630();
  if (plVar4 != (longlong *)0x0) {
    local_res10 = (**(code **)(*plVar4 + 0x18))(plVar4);
    local_res10 = local_res10 + 0x18;
    local_ac0 = DAT_14019f230;
    local_ab8 = DAT_14019f238;
    local_ab0 = DAT_14019f240;
    local_aa8 = DAT_14019f248;
    local_aa4 = DAT_14019f24c;
    local_aa2 = DAT_14019f24e;
    puVar11 = &local_ac0;
    FUN_140146d40(local_a98,1,&DAT_14019f250,0,CONCAT44(uVar10,0x1000),puVar11,param_1,0,1);
    (*DAT_14018c710)(0,local_818,0x1ff);
    sVar5 = strlen(local_818);
    FUN_140001a34(&local_res10,local_818,sVar5 & 0xffffffff);
    FUN_14000ce58(&local_res10,"\\VCDS.exe","\\Scans");
    lVar6 = FUN_14014649c(local_a98);
    lVar9 = local_res10;
    *(longlong *)(lVar6 + 0x50) = local_res10;
    _Var7 = CFileDialog::DoModal(local_a98);
    if (_Var7 == 1) {
      uVar8 = FUN_140147b2c(local_a98,&local_res20);
      FID_conflict_operator_(&local_res18,uVar8);
      LOCK();
      piVar1 = (int *)(local_res20 + -8);
      iVar3 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (iVar3 + -1 < 1) {
        (**(code **)(**(longlong **)(local_res20 + -0x18) + 8))();
      }
      _File = (FILE *)FUN_140156994(local_res18);
      fseek(_File,0,2);
      lVar2 = ftell(_File);
      rewind(_File);
      lVar9 = FUN_14011d5c4(&DAT_140701ec0,lVar2 + 600);
      if (lVar9 == 0) {
        FUN_140146b5c(local_a98);
        LOCK();
        piVar1 = (int *)(local_res10 + -8);
        iVar3 = *piVar1;
        *piVar1 = *piVar1 + -1;
        UNLOCK();
        if (iVar3 + -1 < 1) {
          (**(code **)(**(longlong **)(local_res10 + -0x18) + 8))();
        }
        LOCK();
        piVar1 = (int *)(local_res18 + -8);
        iVar3 = *piVar1;
        *piVar1 = *piVar1 + -1;
        UNLOCK();
        if (iVar3 + -1 < 1) {
          (**(code **)(**(longlong **)(local_res18 + -0x18) + 8))();
        }
      }
      else {
        FUN_140156be0(lVar9,&DAT_14019aa80);
        iVar3 = FUN_140065420(_File,local_818,0x7ff);
        while (uVar10 = (undefined4)((ulonglong)puVar11 >> 0x20), iVar3 != -1) {
          FUN_140156b40(lVar9,local_818);
          FUN_140156b40(lVar9,&DAT_14019b5ec);
          iVar3 = FUN_140065420(_File,local_818,0x7ff);
        }
        fclose(_File);
        FUN_14011d624(&DAT_140701ec0);
        sVar5 = strlen("http://www.ross-tech.com/misc/uploadform.php?serial=");
        FUN_140001a34(&local_res10,"http://www.ross-tech.com/misc/uploadform.php?serial=",
                      sVar5 & 0xffffffff);
        FUN_14000d230(&local_res10,DAT_140702238,*(undefined4 *)(DAT_140702238 + -0x10));
        lVar9 = local_res10;
        (*DAT_14018c918)(0,0,local_res10,0,0,CONCAT44(uVar10,1));
        FUN_140146b5c(local_a98);
        LOCK();
        piVar1 = (int *)(lVar9 + -8);
        iVar3 = *piVar1;
        *piVar1 = *piVar1 + -1;
        UNLOCK();
        if (iVar3 + -1 < 1) {
          (**(code **)(**(longlong **)(lVar9 + -0x18) + 8))();
        }
        LOCK();
        piVar1 = (int *)(local_res18 + -8);
        iVar3 = *piVar1;
        *piVar1 = *piVar1 + -1;
        UNLOCK();
        if (iVar3 + -1 < 1) {
          (**(code **)(**(longlong **)(local_res18 + -0x18) + 8))();
        }
      }
    }
    else {
      FUN_140146b5c(local_a98);
      LOCK();
      piVar1 = (int *)(lVar9 + -8);
      iVar3 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (iVar3 + -1 < 1) {
        (**(code **)(**(longlong **)(lVar9 + -0x18) + 8))();
      }
      LOCK();
      piVar1 = (int *)(local_res18 + -8);
      iVar3 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (iVar3 + -1 < 1) {
        (**(code **)(**(longlong **)(local_res18 + -0x18) + 8))();
      }
    }
    return;
  }
                    /* WARNING: Subroutine does not return */
  FUN_140001000(0x80004005);
}


```

- **XREF from:** `14004cd39` in Function **`FUN_14004c9e4`** (`14004c9e4`)
- **XREF from:** `1400fb003` in Function **`FUN_1400faef0`** (`1400faef0`)

```c
// Function: FUN_1400faef0 @ 1400faef0

void FUN_1400faef0(CWnd *param_1)

{
  int *piVar1;
  int iVar2;
  longlong *plVar3;
  CWnd *pCVar4;
  size_t sVar5;
  longlong local_res10;
  longlong local_res18 [2];
  
  plVar3 = (longlong *)FUN_14013a630();
  if (plVar3 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  local_res18[0] = (**(code **)(*plVar3 + 0x18))(plVar3);
  local_res18[0] = local_res18[0] + 0x18;
  plVar3 = (longlong *)FUN_14013a630();
  if (plVar3 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  local_res10 = (**(code **)(*plVar3 + 0x18))(plVar3);
  local_res10 = local_res10 + 0x18;
  FUN_140003cf0(local_res18,"%02X:%s-%s;",DAT_1401f6818,&DAT_1405a4380,DAT_140639d28);
  pCVar4 = CWnd::GetDlgItem(param_1,0x5d8);
  FUN_140140b14(pCVar4,&local_res10);
  FUN_14000d230(local_res18,local_res10,*(undefined4 *)(local_res10 + -0x10));
  iVar2 = (*DAT_14018ccf8)(*(undefined8 *)(param_1 + 0x128),0x147,0,0);
  FUN_140003cf0(&local_res10," => %s",(&DAT_141155af8)[iVar2]);
  FUN_14000d230(local_res18,local_res10,*(undefined4 *)(local_res10 + -0x10));
  sVar5 = strlen("http://www.ross-tech.com/misc/uploadform.php?serial=");
  FUN_140001a34(&local_res10,"http://www.ross-tech.com/misc/uploadform.php?serial=",
                sVar5 & 0xffffffff);
  FUN_14000d230(&local_res10,DAT_140702238,*(undefined4 *)(DAT_140702238 + -0x10));
  sVar5 = strlen("&carinfo=");
  FUN_14000d230(&local_res10,"&carinfo=",sVar5 & 0xffffffff);
  FUN_14000d230(&local_res10,local_res18[0],*(undefined4 *)(local_res18[0] + -0x10));
  (*DAT_14018c918)(0,0,local_res10,0,0,1);
  LOCK();
  piVar1 = (int *)(local_res10 + -8);
  iVar2 = *piVar1;
  *piVar1 = *piVar1 + -1;
  UNLOCK();
  if (iVar2 + -1 < 1) {
    (**(code **)(**(longlong **)(local_res10 + -0x18) + 8))();
  }
  LOCK();
  piVar1 = (int *)(local_res18[0] + -8);
  iVar2 = *piVar1;
  *piVar1 = *piVar1 + -1;
  UNLOCK();
  if (iVar2 + -1 < 1) {
    (**(code **)(**(longlong **)(local_res18[0] + -0x18) + 8))();
  }
  return;
}


```

- **XREF from:** `1400fb012` in Function **`FUN_1400faef0`** (`1400faef0`)
- **XREF from:** `140112ba3` in Function **`FUN_140112640`** (`140112640`)

```c
// Function: FUN_140112640 @ 140112640

/* WARNING: Globals starting with '_' overlap smaller symbols at the same address */

undefined * FUN_140112640(void)

{
  longlong *plVar1;
  longlong lVar2;
  undefined4 *puVar3;
  size_t sVar4;
  longlong lVar5;
  
  CWnd::CWnd((CWnd *)&DAT_140701ec0);
  _DAT_140701ec0 = &PTR_LAB_1401bc760;
  plVar1 = (longlong *)FUN_14013a630();
  lVar5 = 0;
  if (plVar1 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  lVar2 = (**(code **)(*plVar1 + 0x18))(plVar1);
  DAT_140701fe0 = lVar2 + 0x18;
  plVar1 = (longlong *)FUN_14013a630();
  if (plVar1 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  lVar2 = (**(code **)(*plVar1 + 0x18))(plVar1);
  DAT_140702238 = lVar2 + 0x18;
  plVar1 = (longlong *)FUN_14013a630();
  if (plVar1 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  _DAT_1407022a0 = (**(code **)(*plVar1 + 0x18))(plVar1);
  _DAT_1407022a0 = _DAT_1407022a0 + 0x18;
  _DAT_1407022a8 = 0;
  _DAT_1407022b0 = 0;
  DAT_1407022b8 = 0;
  DAT_1407022c0 = 0;
  _DAT_1407022c8 = 0;
  _DAT_1407022d0 = 0;
  _eh_vector_constructor_iterator_(&DAT_140702350,8,4,FUN_140001730,FUN_1400b2e64);
  plVar1 = (longlong *)FUN_14013a630();
  if (plVar1 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  _DAT_140702530 = (**(code **)(*plVar1 + 0x18))(plVar1);
  _DAT_140702530 = _DAT_140702530 + 0x18;
  plVar1 = (longlong *)FUN_14013a630();
  if (plVar1 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  _DAT_140702540 = (**(code **)(*plVar1 + 0x18))(plVar1);
  _DAT_140702540 = _DAT_140702540 + 0x18;
  plVar1 = (longlong *)FUN_14013a630();
  if (plVar1 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  lVar2 = (**(code **)(*plVar1 + 0x18))(plVar1);
  DAT_140702548 = lVar2 + 0x18;
  plVar1 = (longlong *)FUN_14013a630();
  if (plVar1 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  lVar2 = (**(code **)(*plVar1 + 0x18))(plVar1);
  DAT_140702598 = lVar2 + 0x18;
  plVar1 = (longlong *)FUN_14013a630();
  if (plVar1 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  _DAT_1407025d0 = (**(code **)(*plVar1 + 0x18))(plVar1);
  _DAT_1407025d0 = _DAT_1407025d0 + 0x18;
  plVar1 = (longlong *)FUN_14013a630();
  if (plVar1 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  _DAT_1407025d8 = (**(code **)(*plVar1 + 0x18))(plVar1);
  _DAT_1407025d8 = _DAT_1407025d8 + 0x18;
  plVar1 = (longlong *)FUN_14013a630();
  if (plVar1 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  _DAT_1407025e0 = (**(code **)(*plVar1 + 0x18))(plVar1);
  _DAT_1407025e0 = _DAT_1407025e0 + 0x18;
  plVar1 = (longlong *)FUN_14013a630();
  if (plVar1 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  _DAT_1407025e8 = (**(code **)(*plVar1 + 0x18))(plVar1);
  _DAT_1407025e8 = _DAT_1407025e8 + 0x18;
  _eh_vector_constructor_iterator_(&DAT_1407025f0,8,0x10,FUN_140001730,FUN_1400b2e64);
  plVar1 = (longlong *)FUN_14013a630();
  if (plVar1 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  _DAT_140702670 = (**(code **)(*plVar1 + 0x18))(plVar1);
  _DAT_140702670 = _DAT_140702670 + 0x18;
  DAT_140701f68 = 0;
  DAT_140701f6c = 1;
  _DAT_140701f70 = 0xffffffff;
  DAT_140702224 = (uint)DAT_14020b837;
  _DAT_1407025a8 = (uint)DAT_14020b84d;
  _DAT_1407025ac = (uint)DAT_14020b840;
  _DAT_1407025b0 = (uint)DAT_14020b822;
  _DAT_1407025b4 = (uint)DAT_14020b858;
  _DAT_1407025b8 = (uint)DAT_14020b86b;
  do {
    *(uint *)((longlong)&DAT_14070228c + lVar5) = (uint)(byte)(&DAT_14020b8c7)[lVar5];
    lVar5 = lVar5 + 4;
  } while (lVar5 < 0x10);
  DAT_140702220 = 0;
  DAT_140702228 = 0;
  DAT_140702229 = 0;
  DAT_1407021f0 = 1;
  DAT_140701fee = 0;
  DAT_140701fef = 0;
  DAT_140701fe8 = 0;
  DAT_140701fd0 = 0;
  DAT_14070226c = 0;
  DAT_140702280 = 0;
  DAT_140702278 = 0;
  DAT_14070221c = (ushort)DAT_14020b857;
  DAT_140701fed = 0;
  DAT_140702288 = 0;
  DAT_140702264 = 0xffffffff;
  DAT_140702268 = 0xfffffffe;
  _DAT_1407025bc = 0;
  _DAT_1407025c0 = 0;
  DAT_140702279 = 0;
  DAT_140701fd4 = 0;
  DAT_140702260 = 0;
  DAT_140702493 = 0;
  DAT_140702380 = 0;
  DAT_14070248c = 0;
  DAT_140702491 = 0;
  DAT_140702492 = 0;
  DAT_140702219 = 0;
  DAT_140702385 = 0;
  DAT_1407022dc = 0;
  DAT_1407022dd = 0;
  FUN_140158320(&DAT_140701f8c,0,0x40);
  _DAT_140702528 = 0;
  _DAT_140702538 = 0;
  DAT_14070253a = 0;
  puVar3 = &DAT_1407022f0;
  do {
    puVar3[-4] = 0xffffffff;
    *puVar3 = 0xffffffff;
    puVar3 = puVar3 + 1;
  } while ((longlong)puVar3 < 0x140702300);
  _DAT_140702370 = 0xffffffffffffffff;
  DAT_14070237c = 1;
  DAT_14070237d = 0;
  sVar4 = strlen("");
  FUN_140001a34(&DAT_1407022a0,&DAT_14019aa80,sVar4 & 0xffffffff);
  DAT_140702494 = 0;
  DAT_140702495 = 1;
  DAT_140702496 = 0;
  DAT_14070237e = 0;
  DAT_140702518 = 0;
  _DAT_1407025c4 = 0;
  _DAT_140702678 = 0;
  DAT_140702550 = 0;
  DAT_140702554 = 0;
  DAT_140702558 = 0;
  sVar4 = strlen("support@ross-tech.com");
  FUN_140001a34(&DAT_140702540,"support@ross-tech.com",sVar4 & 0xffffffff);
  sVar4 = strlen("http://www.ross-tech.com/misc/uploadform.php?serial=");
  FUN_140001a34(&DAT_140702548,"http://www.ross-tech.com/misc/uploadform.php?serial=",
                sVar4 & 0xffffffff);
  _DAT_140702560 = 0;
  _DAT_140702568 = 0;
  DAT_140702570 = 0;
  _DAT_140702304 = 0;
  DAT_140702578 = 0;
  DAT_140702580 = 0;
  DAT_140702584 = 0;
  DAT_140702588 = 0;
  DAT_14070258c = 0;
  DAT_140702590 = 0;
  DAT_140702591 = 0;
  DAT_140702592 = 0;
  FUN_140158320(&DAT_140702308,0,0x20);
  FUN_140158320(&DAT_140702328,0,0x20);
  DAT_140702348 = 0;
  DAT_140702384 = 0;
  DAT_140702490 = 0;
  DAT_140701fcc = 0;
  _DAT_140702680 = 0;
  _DAT_140702594 = 0xffffffff;
  return &DAT_140701ec0;
}


```

- **XREF from:** `140112bb2` in Function **`FUN_140112640`** (`140112640`)

### String `"\".\\Scans\""` at `14019f220`
- **XREF from:** `14004c986` in Function **`FUN_14004c8b4`** (`14004c8b4`)

### String `"\"\\Scans\""` at `14019f254`
- **XREF from:** `14004cb1d` in Function **`FUN_14004c9e4`** (`14004c9e4`)

### String `"\"Can't spawn TDIGraph.EXE\""` at `1401aa378`
- **XREF from:** `140059959` in Function **`FUN_1400597c8`** (`1400597c8`)

### String `"\"http://wiki.ross-tech.com/index.php/Control_Module_Maps\""` at `1401aa4a0`
- *(No direct XREFs found)*

### String `"\"Copyright(c) 2004, Ross-Tech LLC\""` at `1401ab448`
- **XREF from:** `140064da5` in Function **`FUN_140064d54`** (`140064d54`)

```c
// Function: FUN_140064d54 @ 140064d54

int FUN_140064d54(void)

{
  byte bVar1;
  bool bVar2;
  int iVar3;
  int iVar4;
  char *pcVar5;
  FILE *_File;
  size_t sVar6;
  char *pcVar7;
  longlong lVar8;
  byte *pbVar9;
  int iVar10;
  undefined1 *puVar11;
  char local_148;
  char local_147 [7];
  undefined1 local_140;
  byte local_108;
  byte local_107;
  byte local_106;
  byte local_105;
  byte local_104;
  byte local_103;
  byte local_102;
  byte local_101;
  char local_ff [247];
  
  pbVar9 = &DAT_14020b884;
  lVar8 = 0;
  do {
    bVar1 = *pbVar9;
    pbVar9 = pbVar9 + 2;
    (&DAT_1405a4280)[lVar8] = (&DAT_14020b8e8)[lVar8] ^ bVar1;
    lVar8 = lVar8 + 1;
  } while ((longlong)pbVar9 < 0x14020ba44);
  FUN_140156be0(&local_148,"Copyright(c) 2004, Ross-Tech LLC");
  if (local_148 != '\0') {
    pcVar5 = &local_148;
    pcVar7 = &DAT_1405a4360;
    do {
      pcVar5 = pcVar5 + 1;
      *pcVar7 = local_148;
      pcVar7 = pcVar7 + 1;
      local_148 = *pcVar5;
    } while (local_148 != '\0');
  }
  iVar10 = 0;
  FUN_140156be0(&DAT_1405a3db0,"CODES.DAT");
  _File = (FILE *)FUN_140156994(&DAT_1405a3db0,&DAT_1401ab47c);
  if (_File == (FILE *)0x0) {
    iVar4 = -1;
  }
  else {
    iVar3 = FUN_14006548c(_File,&local_108);
    iVar4 = iVar10;
    if (iVar3 != -1) {
      puVar11 = &DAT_14025e5e0;
      do {
        sVar6 = strlen((char *)&local_108);
        if ((((((5 < sVar6) && (iVar4 = isdigit((uint)local_108), iVar4 != 0)) &&
              (iVar4 = isdigit((uint)local_107), iVar4 != 0)) &&
             ((iVar4 = isdigit((uint)local_106), iVar4 != 0 &&
              (iVar4 = isdigit((uint)local_105), iVar4 != 0)))) &&
            ((iVar4 = isdigit((uint)local_104), iVar4 != 0 &&
             ((iVar4 = isdigit((uint)local_103), iVar4 != 0 &&
              (iVar4 = isdigit((uint)local_102), iVar4 != 0)))))) &&
           (iVar4 = isdigit((uint)local_101), iVar4 != 0)) {
          strncpy(&local_148,(char *)&local_108,8);
          local_140 = 0;
          FID_conflict_sscanf(&local_148,"%08d",&DAT_140581a70 + (longlong)iVar10 * 4);
          lVar8 = 0;
          if (0 < local_ff[0] + 0xb) {
            do {
              if (0x5e < lVar8) break;
              puVar11[lVar8] = local_ff[lVar8];
              lVar8 = lVar8 + 1;
            } while (lVar8 < local_ff[0] + 0xb);
          }
          puVar11 = puVar11 + 0x5e;
          bVar2 = 35000 < iVar10;
          iVar10 = iVar10 + 1;
          iVar4 = 35000;
          if (bVar2) break;
        }
        iVar3 = FUN_14006548c(_File,&local_108);
        iVar4 = iVar10;
      } while (iVar3 != -1);
    }
    fclose(_File);
  }
  return iVar4;
}


```


### String `"\"HEX-Micro USB<->Serial Adapter\""` at `1401acbf8`
- **XREF from non-function address:** `14007cbb6`

### String `"\"Ross-Tech USB<->Serial Adapter\""` at `1401acc18`
- **XREF from non-function address:** `14007cbd5`

### String `"\"HEX-USB\""` at `1401acc38`
- **XREF from non-function address:** `14007cc02`

### String `"\"KII-USB\""` at `1401acc40`
- **XREF from non-function address:** `14007cc1a`

### String `"\"KEY-USB\""` at `1401acc58`
- **XREF from non-function address:** `14009bb49`
- **XREF from non-function address:** `14009bd2c`
- **XREF from non-function address:** `14009bd3b`
- **XREF from non-function address:** `14009c03f`
- **XREF from non-function address:** `14009c04e`
- **XREF from non-function address:** `14007cc5a`

### String `"\"HC::Test:KMode failed 1\""` at `1401acc70`
- **XREF from non-function address:** `14007d052`

### String `"\"ROSSTECH\""` at `1401acc98`
- **XREF from non-function address:** `14009a598`
- **XREF from non-function address:** `14009bfa9`
- **XREF from non-function address:** `14007d333`
- **XREF from non-function address:** `1400912f3`

### String `"\"Ross-Tech\""` at `1401accb8`
- **XREF from non-function address:** `14007d3c7`
- **XREF from non-function address:** `14007d3d6`

### String `"\" KII-USB\""` at `1401accc8`
- **XREF from non-function address:** `14007d3f1`
- **XREF from non-function address:** `14007d400`

### String `"\"HC::Test:KMode failed 2\""` at `1401accf0`
- **XREF from non-function address:** `14007d5a4`

### String `"\"HexInit: %d\""` at `1401acd20`
- **XREF from non-function address:** `14007e21d`

### String `"\"HC::TurboBaud -1\""` at `1401acd30`
- **XREF from non-function address:** `14007e6ce`

### String `"\"HC::TurboBaud -2\""` at `1401acd48`
- **XREF from non-function address:** `14007e6f9`

### String `"\"HC::SendCommand -1\""` at `1401acd60`
- **XREF from non-function address:** `14007e750`

### String `"\"HC::GetVersion -1\""` at `1401acd78`
- **XREF from:** `14007ea38` in Function **`FUN_14007e988`** (`14007e988`)

```c
// Function: FUN_14007e988 @ 14007e988

/* WARNING: Globals starting with '_' overlap smaller symbols at the same address */

undefined8 FUN_14007e988(longlong *param_1)

{
  int iVar1;
  undefined8 uVar2;
  size_t sVar3;
  double dVar4;
  float local_res8 [2];
  int local_res10 [2];
  uint local_res18 [2];
  undefined1 local_res20 [8];
  char local_128;
  byte local_127;
  byte local_126;
  char local_125;
  
  FUN_1400a143c(0xfa);
  FUN_140111ddc(DAT_1405a55e4);
  local_128 = '\x01';
  local_127 = 2;
  (**(code **)(*param_1 + 0x108))(param_1,&local_128);
  DAT_1401f6e4c = 0x2ee;
  iVar1 = (**(code **)(*param_1 + 0x110))(param_1,&local_128);
  if (iVar1 < 0) {
    if (DAT_1405a55e4 == 8) {
      FUN_1400a143c(0xfa);
      FUN_140111ddc(DAT_1405a55e4);
      local_128 = '\x01';
      local_127 = 2;
      (**(code **)(*param_1 + 0x108))(param_1,&local_128);
      iVar1 = (**(code **)(*param_1 + 0x110))(param_1,&local_128);
    }
    if (iVar1 < 0) {
      uVar2 = FUN_140001790(local_res20,"HC::GetVersion -1");
      FUN_140098d5c(param_1,uVar2);
      return 0xffffffff;
    }
  }
  if (local_128 != '\x02') {
    uVar2 = FUN_140001790(local_res20,"HC::GetVersion -2");
    FUN_140098d5c(param_1,uVar2);
    return 0xfffffffe;
  }
  *(uint *)((longlong)param_1 + 0x1c4) = (uint)CONCAT11(local_127,local_126);
  dVar4 = (double)local_126 * DAT_1401bdfd8 + (double)local_127;
  FID_conflict_sscanf("1.96","%d.%d",local_res10,local_res18);
  *(uint *)((longlong)param_1 + 0x1bc) = local_res10[0] << 8 | local_res18[0];
  FUN_140003cf0(param_1 + 5,"%1.2f",dVar4);
  if (local_125 != 'C') {
    if ((local_125 == 'D') || (local_125 == 'F')) {
      *(undefined1 *)((longlong)param_1 + 0x4a) = 1;
      sVar3 = strlen("+CAN");
      FUN_14000d230(param_1 + 3,&DAT_14019e6b0,sVar3 & 0xffffffff);
      goto LAB_14007ebb7;
    }
    iVar1 = FUN_1400800d4(param_1);
    if (iVar1 == 1) {
      *(undefined1 *)((longlong)param_1 + 0x4a) = 1;
      sVar3 = strlen("+CAN");
      FUN_14000d230(param_1 + 3,&DAT_14019e6b0,sVar3 & 0xffffffff);
      iVar1 = (**(code **)(*param_1 + 0x68))(param_1);
      if (iVar1 != 1) {
        uVar2 = FUN_140001790(local_res20,"HC::GetVersion:KMode failed 1");
        FUN_140098d5c(param_1,uVar2);
      }
      goto LAB_14007ebb7;
    }
  }
  *(undefined1 *)((longlong)param_1 + 0x4a) = 0;
LAB_14007ebb7:
  FID_conflict_sscanf("1.96","%f",local_res8);
  if (local_125 == 'F') {
    *(undefined1 *)(param_1 + 0x36) = 1;
    FID_conflict_sscanf("1.96","%f",local_res8);
  }
  if ((float)dVar4 + _DAT_1401bfd38 < local_res8[0]) {
    *(undefined1 *)((longlong)param_1 + 0x39) = 1;
  }
  return 0;
}


```


### String `"\"HC::GetVersion -2\""` at `1401acd90`
- **XREF from:** `14007ea66` in Function **`FUN_14007e988`** (`14007e988`)

### String `"\"HC::GetVersion:KMode failed 1\""` at `1401acdb8`
- **XREF from:** `14007eb6d` in Function **`FUN_14007e988`** (`14007e988`)

### String `"\"HC::Com115 -1\""` at `1401acdd8`
- **XREF from:** `14007ec83` in Function **`FUN_14007ec2c`** (`14007ec2c`)

```c
// Function: FUN_14007ec2c @ 14007ec2c

undefined8 FUN_14007ec2c(longlong *param_1)

{
  int iVar1;
  undefined8 uVar2;
  undefined1 local_res8 [32];
  char local_108 [256];
  
  local_108[0] = '\x05';
  local_108[1] = 3;
  local_108[2] = 0;
  local_108[3] = 0xc2;
  local_108[4] = 1;
  local_108[5] = 0;
  (**(code **)(*param_1 + 0x108))(param_1,local_108);
  DAT_1401f6e4c = 0x2ee;
  iVar1 = (**(code **)(*param_1 + 0x110))(param_1,local_108);
  if (iVar1 < 0) {
    uVar2 = FUN_140001790(local_res8,"HC::Com115 -1");
    FUN_140098d5c(param_1,uVar2);
    uVar2 = 0xffffffff;
  }
  else if (local_108[0] == -2) {
    FUN_140111ed0(DAT_1405a55e4,0x1c200);
    DAT_1401f6e4c = 0x2ee;
    iVar1 = (**(code **)(*param_1 + 0x110))(param_1,local_108);
    if (iVar1 < 0) {
      uVar2 = FUN_140001790(local_res8,"HC::Com115 -3");
      FUN_140098d5c(param_1,uVar2);
      uVar2 = 0xfffffffd;
    }
    else if (local_108[0] == -3) {
      local_108[0] = '\x01';
      local_108[1] = 0xfe;
      (**(code **)(*param_1 + 0x108))(param_1,local_108);
      uVar2 = 0;
    }
    else {
      uVar2 = FUN_140001790(local_res8,"HC::Com115 -4");
      FUN_140098d5c(param_1,uVar2);
      uVar2 = 0xfffffffc;
    }
  }
  else {
    uVar2 = FUN_140001790(local_res8,"HC::Com115 -2");
    FUN_140098d5c(param_1,uVar2);
    uVar2 = 0xfffffffe;
  }
  return uVar2;
}


```


### String `"\"HC::Com115 -2\""` at `1401acde8`
- **XREF from:** `14007ecb1` in Function **`FUN_14007ec2c`** (`14007ec2c`)

### String `"\"HC::Com115 -3\""` at `1401acdf8`
- **XREF from:** `14007ed09` in Function **`FUN_14007ec2c`** (`14007ec2c`)

### String `"\"HC::Com115 -4\""` at `1401ace08`
- **XREF from:** `14007ed36` in Function **`FUN_14007ec2c`** (`14007ec2c`)

### String `"\"HC::KLineTest -1\""` at `1401ace18`
- **XREF from:** `14007ee4a` in Function **`FUN_14007ed84`** (`14007ed84`)

```c
// Function: FUN_14007ed84 @ 14007ed84

undefined8 FUN_14007ed84(longlong *param_1)

{
  int *piVar1;
  int iVar2;
  longlong *plVar3;
  undefined8 *puVar4;
  undefined8 uVar5;
  size_t sVar6;
  longlong lVar7;
  undefined8 *puVar8;
  longlong local_res10;
  undefined8 *local_res18;
  undefined1 local_res20 [8];
  undefined1 local_128 [8];
  undefined8 local_120;
  char local_118;
  char local_117;
  char local_116;
  
  local_120 = 0xfffffffffffffffe;
  plVar3 = (longlong *)FUN_14013a630();
  if (plVar3 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  local_res10 = (**(code **)(*plVar3 + 0x18))(plVar3);
  local_res10 = local_res10 + 0x18;
  plVar3 = (longlong *)FUN_14013a630();
  if (plVar3 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  puVar4 = (undefined8 *)(**(code **)(*plVar3 + 0x18))(plVar3);
  puVar8 = puVar4 + 3;
  local_res18 = puVar8;
  FUN_1400a143c(0x32);
  local_118 = '\x01';
  local_117 = -0x7e;
  (**(code **)(*param_1 + 0x108))(param_1,&local_118);
  DAT_1401f6e4c = 0x2ee;
  iVar2 = (**(code **)(*param_1 + 0x110))(param_1,&local_118);
  if (iVar2 < 0) {
    uVar5 = FUN_140001790(local_res20,"HC::KLineTest -1");
    FUN_140098d5c(param_1,uVar5);
    LOCK();
    piVar1 = (int *)(puVar4 + 2);
    iVar2 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar2 + -1 < 1) {
      (**(code **)(*(longlong *)*puVar4 + 8))();
    }
    LOCK();
    piVar1 = (int *)(local_res10 + -8);
    iVar2 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar2 + -1 < 1) {
      (**(code **)(**(longlong **)(local_res10 + -0x18) + 8))();
    }
    uVar5 = 0xffffffff;
  }
  else if (local_118 == -0x7e) {
    if (local_117 == '\0') {
      *(undefined4 *)((longlong)param_1 + 0x3c) = 0;
      lVar7 = FUN_14013a61c(0xd2);
      if (lVar7 != 0) {
        FUN_1400018a8(&local_res10,lVar7,0xd2);
      }
    }
    else if (local_117 == '\x01') {
      *(undefined4 *)((longlong)param_1 + 0x3c) = 1;
      lVar7 = FUN_14013a61c(0xd3);
      if (lVar7 != 0) {
        FUN_1400018a8(&local_res10,lVar7,0xd3);
      }
    }
    else if (local_117 == '\x02') {
      *(undefined4 *)((longlong)param_1 + 0x3c) = 2;
      lVar7 = FUN_14013a61c(0xd4);
      if (lVar7 != 0) {
        FUN_1400018a8(&local_res10,lVar7,0xd4);
      }
    }
    else {
      *(undefined4 *)((longlong)param_1 + 0x3c) = 0xffffffff;
      sVar6 = strlen("");
      FUN_140001a34(&local_res10,&DAT_14019aa80,sVar6 & 0xffffffff);
    }
    FID_conflict_operator_(param_1 + 6,&local_res10);
    if (local_116 == '\0') {
      *(undefined4 *)(param_1 + 8) = 0;
      lVar7 = FUN_14013a61c(0xd5);
      if (lVar7 != 0) {
        FUN_1400018a8(&local_res10,lVar7,0xd5);
      }
    }
    else if (local_116 == '\x01') {
      *(undefined4 *)(param_1 + 8) = 1;
      lVar7 = FUN_14013a61c(0xd6);
      if (lVar7 != 0) {
        FUN_1400018a8(&local_res10,lVar7,0xd6);
      }
    }
    else if (local_116 == '\x02') {
      *(undefined4 *)(param_1 + 8) = 2;
      lVar7 = FUN_14013a61c(0xd7);
      if (lVar7 != 0) {
        FUN_1400018a8(&local_res10,lVar7,0xd7);
      }
    }
    else {
      *(undefined4 *)(param_1 + 8) = 0xffffffff;
      sVar6 = strlen("");
      FUN_140001a34(&local_res10,&DAT_14019aa80,sVar6 & 0xffffffff);
    }
    FUN_14000d230(param_1 + 6,local_res10,*(undefined4 *)(local_res10 + -0x10));
    if (*(char *)((longlong)param_1 + 0x4a) == '\0') {
      lVar7 = FUN_14013a61c(0x365);
      if (lVar7 != 0) {
        FUN_1400018a8(&local_res18,lVar7,0x365);
        puVar8 = local_res18;
      }
      FUN_140003cf0(&local_res10,"\n    CAN: %s",puVar8);
      FUN_14000d230(param_1 + 6,local_res10,*(undefined4 *)(local_res10 + -0x10));
    }
    if ((*(int *)((longlong)param_1 + 0x3c) == 0) && ((int)param_1[8] == 0)) {
      lVar7 = FUN_14013a61c(0x1d8);
      if (lVar7 != 0) {
        FUN_1400018a8(param_1 + 4,lVar7,0x1d8);
      }
    }
    else {
      lVar7 = FUN_14013a61c(0x1d9);
      if (lVar7 != 0) {
        FUN_1400018a8(param_1 + 4,lVar7,0x1d9);
      }
    }
    LOCK();
    piVar1 = (int *)(puVar8 + -1);
    iVar2 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar2 + -1 < 1) {
      (**(code **)(*(longlong *)puVar8[-3] + 8))();
    }
    LOCK();
    piVar1 = (int *)(local_res10 + -8);
    iVar2 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar2 + -1 < 1) {
      (**(code **)(**(longlong **)(local_res10 + -0x18) + 8))();
    }
    uVar5 = 0;
  }
  else {
    uVar5 = FUN_140001790(local_128,"HC::KLineTest -2");
    FUN_140098d5c(param_1,uVar5);
    LOCK();
    piVar1 = (int *)(puVar4 + 2);
    iVar2 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar2 + -1 < 1) {
      (**(code **)(*(longlong *)*puVar4 + 8))();
    }
    LOCK();
    piVar1 = (int *)(local_res10 + -8);
    iVar2 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar2 + -1 < 1) {
      (**(code **)(**(longlong **)(local_res10 + -0x18) + 8))();
    }
    uVar5 = 0xfffffffe;
  }
  return uVar5;
}


```


### String `"\"HC::KLineTest -2\""` at `1401ace30`
- **XREF from:** `14007eeb9` in Function **`FUN_14007ed84`** (`14007ed84`)

### String `"\"\n    CAN: %s\""` at `1401ace48`
- **XREF from:** `14009d783` in Function **`FUN_14009d454`** (`14009d454`)

```c
// Function: FUN_14009d454 @ 14009d454

undefined8 FUN_14009d454(longlong param_1)

{
  int *piVar1;
  byte bVar2;
  int iVar3;
  longlong *plVar4;
  undefined8 *puVar5;
  undefined8 uVar6;
  size_t sVar7;
  longlong lVar8;
  byte bVar9;
  longlong local_res10;
  undefined8 *local_res18;
  undefined1 local_res20 [8];
  undefined8 local_a8;
  undefined2 local_a0;
  
  plVar4 = (longlong *)FUN_14013a630();
  if (plVar4 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  local_res10 = (**(code **)(*plVar4 + 0x18))(plVar4);
  local_res10 = local_res10 + 0x18;
  plVar4 = (longlong *)FUN_14013a630();
  if (plVar4 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  puVar5 = (undefined8 *)(**(code **)(*plVar4 + 0x18))(plVar4);
  local_res18 = puVar5 + 3;
  FUN_1400a143c(0xfa);
  local_a8 = CONCAT62(local_a8._2_6_,0x7401);
  FUN_14009d1d0(param_1,&local_a8);
  local_a8 = 0;
  local_a0 = 0;
  FUN_1400a143c(0x2ee);
  iVar3 = FUN_14009d080(param_1,&local_a8);
  if (iVar3 == 0) {
    bVar2 = local_a8._2_1_;
    if ((local_a8 & 0x30000) == 0) {
      *(undefined4 *)(param_1 + 0x3c) = 0;
      lVar8 = FUN_14013a61c(0xd2);
      if (lVar8 != 0) {
        FUN_1400018a8(&local_res10,lVar8,0xd2);
      }
    }
    else if ((local_a8._2_1_ & 3) == 1) {
      *(undefined4 *)(param_1 + 0x3c) = 2;
      lVar8 = FUN_14013a61c(0xd4);
      if (lVar8 != 0) {
        FUN_1400018a8(&local_res10,lVar8,0xd4);
      }
    }
    else if ((local_a8._2_1_ & 3) == 2) {
      *(undefined4 *)(param_1 + 0x3c) = 1;
      lVar8 = FUN_14013a61c(0xd3);
      if (lVar8 != 0) {
        FUN_1400018a8(&local_res10,lVar8,0xd3);
      }
    }
    else {
      *(undefined4 *)(param_1 + 0x3c) = 0xffffffff;
      sVar7 = strlen("");
      FUN_140001a34(&local_res10,&DAT_14019aa80,sVar7 & 0xffffffff);
    }
    FID_conflict_operator_(param_1 + 0x30,&local_res10);
    bVar9 = bVar2 >> 2 & 3;
    if ((bVar2 >> 2 & 3) == 0) {
      *(undefined4 *)(param_1 + 0x40) = 0;
      lVar8 = FUN_14013a61c(0xd5);
      if (lVar8 != 0) {
        FUN_1400018a8(&local_res10,lVar8,0xd5);
      }
    }
    else if (bVar9 == 1) {
      *(undefined4 *)(param_1 + 0x40) = 2;
      lVar8 = FUN_14013a61c(0xd7);
      if (lVar8 != 0) {
        FUN_1400018a8(&local_res10,lVar8,0xd7);
      }
    }
    else if (bVar9 == 2) {
      *(undefined4 *)(param_1 + 0x40) = 1;
      lVar8 = FUN_14013a61c(0xd6);
      if (lVar8 != 0) {
        FUN_1400018a8(&local_res10,lVar8,0xd6);
      }
    }
    else {
      *(undefined4 *)(param_1 + 0x40) = 0xffffffff;
      sVar7 = strlen("");
      FUN_140001a34(&local_res10,&DAT_14019aa80,sVar7 & 0xffffffff);
    }
    FUN_14000d230(param_1 + 0x30,local_res10,*(undefined4 *)(local_res10 + -0x10));
    lVar8 = FUN_14013a61c(0x365);
    puVar5 = puVar5 + 3;
    if (lVar8 != 0) {
      FUN_1400018a8(&local_res18,lVar8,0x365);
      puVar5 = local_res18;
    }
    FUN_140003cf0(&local_res10,"\n    CAN: %s",puVar5);
    FUN_14000d230(param_1 + 0x30,local_res10,*(undefined4 *)(local_res10 + -0x10));
    if ((*(int *)(param_1 + 0x3c) == 0) && (*(int *)(param_1 + 0x40) == 0)) {
      lVar8 = FUN_14013a61c(0x1d8);
      if (lVar8 != 0) {
        FUN_1400018a8(param_1 + 0x20,lVar8,0x1d8);
      }
    }
    else {
      lVar8 = FUN_14013a61c(0x1d9);
      if (lVar8 != 0) {
        FUN_1400018a8(param_1 + 0x20,lVar8,0x1d9);
      }
    }
    LOCK();
    piVar1 = (int *)(puVar5 + -1);
    iVar3 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar3 + -1 < 1) {
      (**(code **)(*(longlong *)puVar5[-3] + 8))();
    }
    LOCK();
    piVar1 = (int *)(local_res10 + -8);
    iVar3 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar3 + -1 < 1) {
      (**(code **)(**(longlong **)(local_res10 + -0x18) + 8))();
    }
    uVar6 = 0;
  }
  else {
    uVar6 = FUN_140001790(local_res20,"KC::KLineTest() -2");
    FUN_140098d5c(param_1,uVar6);
    LOCK();
    piVar1 = (int *)(puVar5 + 2);
    iVar3 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar3 + -1 < 1) {
      (**(code **)(*(longlong *)*puVar5 + 8))();
    }
    LOCK();
    piVar1 = (int *)(local_res10 + -8);
    iVar3 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar3 + -1 < 1) {
      (**(code **)(**(longlong **)(local_res10 + -0x18) + 8))();
    }
    uVar6 = 0xfffffffe;
  }
  return uVar6;
}


```

- **XREF from:** `14007f10e` in Function **`FUN_14007ed84`** (`14007ed84`)

### String `"\"HC::Reset:KMode failed\""` at `1401ace58`
- **XREF from:** `14007f784` in Function **`FUN_14007f758`** (`14007f758`)

```c
// Function: FUN_14007f758 @ 14007f758

undefined8 FUN_14007f758(longlong *param_1)

{
  int iVar1;
  undefined8 uVar2;
  undefined1 local_res8 [8];
  char local_108 [256];
  
  DAT_1407026a0 = DAT_1405a554c;
  if (DAT_1405a554c == 2) {
    iVar1 = (**(code **)(*param_1 + 0x68))();
    if (iVar1 != 1) {
      uVar2 = FUN_140001790(local_res8,"HC::Reset:KMode failed");
      FUN_140098d5c(param_1,uVar2);
    }
    DAT_1405a554c = -1;
  }
  if (*(char *)((longlong)param_1 + 0x9ce) != '\0') {
    FUN_140111f1c(DAT_1405a55e4,0x41);
    FUN_1400a143c(1);
    FUN_140111f1c(DAT_1405a55e4,0x43);
    FUN_1400a143c(1);
    FUN_140111ddc(DAT_1405a55e4);
  }
  local_108[0] = '\x01';
  local_108[1] = 8;
  (**(code **)(*param_1 + 0x108))(param_1,local_108);
  DAT_1401f6e4c = 0x2ee;
  iVar1 = (**(code **)(*param_1 + 0x110))(param_1,local_108);
  if (iVar1 < 0) {
LAB_14007f827:
    uVar2 = FUN_140001790(local_res8,"HC::Reset -1");
    FUN_140098d5c(param_1,uVar2);
    uVar2 = 0xffffffff;
  }
  else {
    if (local_108[0] != -2) {
LAB_14007f855:
      uVar2 = FUN_140001790(local_res8,"HC::Reset -2");
      FUN_140098d5c(param_1,uVar2);
      return 0xfffffffe;
    }
    FUN_1400a143c(0x96);
    FUN_140111ddc(DAT_1405a55e4);
    FUN_140158320(param_1 + 0x39,0,0x800);
    *(undefined4 *)(param_1 + 0x139) = 0;
    if (DAT_14070237d != '\0') {
      (**(code **)(*param_1 + 0x68))(param_1);
      FUN_1400a143c(0x32);
      FUN_140083e2c(param_1);
      FUN_14007f1d8(param_1);
      local_108[0] = '\x01';
      local_108[1] = 8;
      (**(code **)(*param_1 + 0x108))(param_1,local_108);
      DAT_1401f6e4c = 0x2ee;
      iVar1 = (**(code **)(*param_1 + 0x110))(param_1,local_108);
      if (iVar1 < 0) goto LAB_14007f827;
      if (local_108[0] != -2) goto LAB_14007f855;
      FUN_1400a143c(0x96);
      FUN_140111ddc(DAT_1405a55e4);
      FUN_140158320(param_1 + 0x39,0,0x800);
      *(undefined4 *)(param_1 + 0x139) = 0;
    }
    uVar2 = 0;
  }
  return uVar2;
}


```


### String `"\"HC::Reset -1\""` at `1401ace70`
- **XREF from:** `14007f827` in Function **`FUN_14007f758`** (`14007f758`)

### String `"\"HC::Reset -2\""` at `1401ace80`
- **XREF from:** `14007f855` in Function **`FUN_14007f758`** (`14007f758`)

### String `"\"HC::KMode Fail try %d\""` at `1401ace98`
- **XREF from non-function address:** `140080050`

### String `"\"HC::SetRcvMask -3\""` at `1401acec0`
- **XREF from:** `1400808c5` in Function **`FUN_140080870`** (`140080870`)

```c
// Function: FUN_140080870 @ 140080870

undefined8
FUN_140080870(longlong *param_1,undefined1 param_2,undefined1 param_3,undefined1 param_4,
             undefined1 param_5)

{
  undefined8 uVar1;
  undefined1 local_res8 [32];
  char local_48 [3];
  undefined1 local_45;
  undefined1 local_44;
  undefined1 local_43;
  undefined1 local_42;
  
  local_48[0] = '\x06';
  local_48[1] = 0xb3;
  local_43 = param_5;
  local_42 = 0;
  local_48[2] = param_2;
  local_45 = param_3;
  local_44 = param_4;
  (**(code **)(*param_1 + 0x108))(param_1,local_48);
  (**(code **)(*param_1 + 0x110))(param_1,local_48);
  if (local_48[0] == -2) {
    uVar1 = 1;
  }
  else {
    uVar1 = FUN_140001790(local_res8,"HC::SetRcvMask -3");
    FUN_140098d5c(param_1,uVar1);
    uVar1 = 0xfffffffd;
  }
  return uVar1;
}


```


### String `"\"HC::SetRcvFilter -4\""` at `1401aced8`
- **XREF from:** `140080959` in Function **`FUN_1400808f4`** (`1400808f4`)

```c
// Function: FUN_1400808f4 @ 1400808f4

undefined8
FUN_1400808f4(longlong *param_1,undefined1 param_2,undefined1 param_3,undefined1 param_4,
             undefined1 param_5)

{
  undefined8 uVar1;
  undefined1 local_res8 [32];
  char local_48 [3];
  undefined1 local_45;
  undefined1 local_44;
  undefined1 local_43;
  undefined1 local_42;
  
  local_48[0] = '\x06';
  local_48[1] = 0xb4;
  local_43 = param_5;
  local_42 = 0;
  local_48[2] = param_2;
  local_45 = param_3;
  local_44 = param_4;
  (**(code **)(*param_1 + 0x108))(param_1,local_48);
  FUN_140158320(local_48,0,0x40);
  (**(code **)(*param_1 + 0x110))(param_1,local_48);
  if (local_48[0] == -2) {
    uVar1 = 1;
  }
  else {
    uVar1 = FUN_140001790(local_res8,"HC::SetRcvFilter -4");
    FUN_140098d5c(param_1,uVar1);
    uVar1 = 0xfffffffc;
  }
  return uVar1;
}


```


### String `"\"\n**** Address %02X-%02X CAN Error %02X in GetCANMsg()\""` at `1401acf18`
- **XREF from non-function address:** `140080b8f`
- **XREF from non-function address:** `140092cac`

### String `"\"**** Address %02X-%02X Unexpected Message from HC: %02X in GetCANMsg()\""` at `1401acf50`
- **XREF from non-function address:** `140080c78`
- **XREF from non-function address:** `140092d73`

### String `"\"HC::SCM -1\""` at `1401acf98`
- **XREF from non-function address:** `140081047`

### String `"\"HC::SCM -3\""` at `1401acfa8`
- **XREF from:** `1400924ea` in Function **`FUN_140092120`** (`140092120`)

```c
// Function: FUN_140092120 @ 140092120

undefined8 FUN_140092120(longlong *param_1,uint *param_2)

{
  int *piVar1;
  int iVar2;
  undefined4 uVar3;
  undefined4 uVar4;
  longlong *plVar5;
  longlong lVar6;
  undefined8 *puVar7;
  size_t sVar8;
  byte bVar9;
  uint uVar10;
  ulonglong uVar11;
  ushort uVar12;
  ulonglong uVar13;
  ulonglong uVar14;
  ulonglong local_res18;
  undefined8 *local_res20;
  undefined1 *in_stack_ffffffffffffff18;
  undefined1 *puVar15;
  char local_d8 [64];
  undefined1 local_98;
  undefined1 local_97;
  byte local_96 [62];
  undefined1 local_58 [24];
  undefined8 local_40;
  
  local_40 = 0xfffffffffffffffe;
  plVar5 = (longlong *)FUN_14013a630();
  uVar14 = 0;
  uVar10 = 0;
  if (plVar5 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  lVar6 = (**(code **)(*plVar5 + 0x18))(plVar5);
  local_res18 = lVar6 + 0x18;
  plVar5 = (longlong *)FUN_14013a630();
  if (plVar5 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  puVar7 = (undefined8 *)(**(code **)(*plVar5 + 0x18))(plVar5);
  local_res20 = puVar7 + 3;
  do {
    local_d8[0] = '\0';
    DAT_1401f6e4c = 0;
    (**(code **)(*param_1 + 0x110))(param_1,local_d8);
    if (local_d8[0] != 'S') break;
    in_stack_ffffffffffffff18 = local_58;
    FUN_140094078(param_1,1,0,local_d8,in_stack_ffffffffffffff18);
  } while (local_d8[0] == 'S');
  if (DAT_1401f6814 == 1) {
    if (*(char *)((longlong)param_2 + 5) != '\x01') goto LAB_14009228b;
    if (*(char *)((longlong)param_2 + 6) != -0x5d) goto LAB_140092223;
  }
  else {
LAB_140092223:
    if (((DAT_1401f6814 != 2) || (*(char *)((longlong)param_2 + 6) != '\x02')) ||
       (*(char *)((longlong)param_2 + 7) != '>')) {
LAB_14009228b:
      uVar13 = local_res18 & 0xffffffff;
      goto LAB_140092292;
    }
  }
  lVar6 = FUN_14011b248(&DAT_140701ec0);
  uVar13 = uVar14;
  if (5000 < lVar6 - param_1[0x140]) {
    while (DAT_140631ef8 == '\0') {
      FUN_140091ef8(param_1);
      iVar2 = (int)uVar13;
      uVar13 = (ulonglong)(iVar2 + 1);
      if ((3 < iVar2) || (lVar6 = FUN_14011b248(&DAT_140701ec0), lVar6 - param_1[0x140] < 0x1389))
      break;
    }
  }
LAB_140092292:
  do {
    uVar3 = (undefined4)((ulonglong)in_stack_ffffffffffffff18 >> 0x20);
    local_d8[0] = '\0';
    DAT_1401f6e4c = 0;
    (**(code **)(*param_1 + 0x110))(param_1,local_d8);
    if (local_d8[0] != 'S') break;
    in_stack_ffffffffffffff18 = local_58;
    FUN_140094078(param_1,1,0,local_d8,in_stack_ffffffffffffff18);
    uVar3 = (undefined4)((ulonglong)in_stack_ffffffffffffff18 >> 0x20);
  } while (local_d8[0] == 'S');
  DAT_1401f6e4c = 0x32;
  if ((char)param_2[1] == '\x01') {
    uVar10 = *param_2 & 0x3ffff;
    uVar12 = (ushort)(*param_2 >> 0x12);
  }
  else {
    uVar12 = (ushort)*param_2 & 0x7ff;
  }
  bVar9 = (byte)(uVar10 >> 0xd);
  local_96[4] = bVar9 | 0x40;
  if ((char)param_2[1] == '\x01') {
    local_96[4] = bVar9 | 0xc0;
  }
  local_98 = 0x11;
  local_97 = 0x53;
  local_96[0] = 0;
  local_96[1] = (char)uVar12;
  local_96[2] = (byte)(uVar12 >> 8) & 7 | (char)uVar10 << 3;
  local_96[3] = (char)(uVar10 >> 5);
  local_96[5] = *(byte *)((longlong)param_2 + 5);
  if (local_96[5] != 0) {
    memcpy(local_96 + 6,(void *)((longlong)param_2 + 6),(ulonglong)local_96[5]);
  }
  local_96[0xe] = (char)param_1[0x13f];
  local_96[0xf] = (char)((uint)(int)param_1[0x13f] >> 8);
  uVar11 = uVar14;
  do {
    local_96[uVar11] = local_96[uVar11] ^ *(byte *)(uVar11 + 0x9b5 + (longlong)param_1);
    uVar11 = uVar11 + 1;
  } while ((longlong)uVar11 < 0x10);
  (**(code **)(*param_1 + 0x108))(param_1,&local_98);
  iVar2 = (**(code **)(*param_1 + 0x110))(param_1,local_d8);
  if (iVar2 < 0) {
    uVar14 = (ulonglong)DAT_1401f6818;
    uVar3 = FUN_140061688(uVar14);
    FUN_140003cf0(&local_res18,"\n**** Address %02X-%02X GetResponse() failed in SendCANMsg()",
                  uVar14 & 0xffffffff,uVar3);
    FUN_1400a1318();
    FUN_14004d0dc(&local_res18,10);
    FUN_140156b40(&DAT_1405a5df0,local_res18);
    FUN_1400a1378(1);
    FUN_1401215b8(&DAT_140701ec0,2,0x12,0xffffffff);
    LOCK();
    piVar1 = (int *)(puVar7 + 2);
    iVar2 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar2 + -1 < 1) {
      (**(code **)(*(longlong *)*puVar7 + 8))();
    }
    LOCK();
    piVar1 = (int *)(local_res18 - 8);
    iVar2 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar2 + -1 < 1) {
      (**(code **)(**(longlong **)(local_res18 - 0x18) + 8))();
    }
  }
  else {
    do {
      if (local_d8[0] != 'S') {
        if (local_d8[0] == -2) {
          *(int *)(param_1 + 0x13f) = (int)param_1[0x13f] + 1;
          if (0xffff < (int)param_1[0x13f]) {
            *(undefined4 *)(param_1 + 0x13f) = 0;
          }
          LOCK();
          piVar1 = (int *)(puVar7 + 2);
          iVar2 = *piVar1;
          *piVar1 = *piVar1 + -1;
          UNLOCK();
          if (iVar2 + -1 < 1) {
            (**(code **)(*(longlong *)*puVar7 + 8))();
          }
          LOCK();
          piVar1 = (int *)(local_res18 - 8);
          iVar2 = *piVar1;
          *piVar1 = *piVar1 + -1;
          UNLOCK();
          if (iVar2 + -1 < 1) {
            (**(code **)(**(longlong **)(local_res18 - 0x18) + 8))();
          }
          return 1;
        }
        if (local_d8[0] == -0x47) {
          uVar10 = (uint)(byte)local_d8[1];
          uVar14 = (ulonglong)DAT_1401f6818;
          uVar4 = FUN_140061688(uVar14);
          FUN_140003cf0(&local_res18,"**** Address %02X-%02X CAN Error %02X in SendCANMsg()",
                        uVar14 & 0xffffffff,uVar4,CONCAT44(uVar3,uVar10));
          FUN_1400a1378(0);
          FUN_1400a1318();
          FUN_140156b40(&DAT_1405a5df0,local_res18);
          FUN_1400a1378(1);
          *(char *)((longlong)param_1 + 0x9b4) = local_d8[1];
          LOCK();
          piVar1 = (int *)(puVar7 + 2);
          iVar2 = *piVar1;
          *piVar1 = *piVar1 + -1;
          UNLOCK();
          if (iVar2 + -1 < 1) {
            (**(code **)(*(longlong *)*puVar7 + 8))();
          }
          LOCK();
          piVar1 = (int *)(local_res18 - 8);
          iVar2 = *piVar1;
          *piVar1 = *piVar1 + -1;
          UNLOCK();
          if (iVar2 + -1 < 1) {
            (**(code **)(**(longlong **)(local_res18 - 0x18) + 8))();
          }
          return 0;
        }
        FUN_140003cf0(&local_res18,"Resp[0]=%02X, CPC=%d, LCE=%02X",local_d8[0],(int)param_1[0x13f],
                      CONCAT44(uVar3,(uint)*(byte *)((longlong)param_1 + 0x9b4)));
        FUN_1400a1378(0);
        FUN_1400a1318();
        FUN_140156b40(&DAT_1405a5df0,local_res18);
        FUN_1400a1378(1);
        *(int *)(param_1 + 0x13f) = (int)param_1[0x13f] + -1;
        do {
          if (((local_d8[0] != -1) || (*(char *)((longlong)param_1 + 0x9b4) != '\0')) ||
             (4 < (int)uVar13)) {
            sVar8 = strlen("Non-ack resp(2):");
            FUN_140001a34(&local_res18,"Non-ack resp(2):",sVar8 & 0xffffffff);
            do {
              FUN_140003cf0(&local_res20," %02X",local_d8[uVar14]);
              puVar7 = local_res20;
              FUN_14000d230(&local_res18,local_res20,*(undefined4 *)(local_res20 + -2));
              uVar14 = uVar14 + 1;
            } while ((longlong)uVar14 < 0x20);
            FUN_1400a1378(0);
            FUN_1400a1318();
            FUN_140156b40(&DAT_1405a5df0,local_res18);
            FUN_1400a1378(1);
            LOCK();
            piVar1 = (int *)(puVar7 + -1);
            iVar2 = *piVar1;
            *piVar1 = *piVar1 + -1;
            UNLOCK();
            if (iVar2 + -1 < 1) {
              (**(code **)(*(longlong *)puVar7[-3] + 8))();
            }
            LOCK();
            piVar1 = (int *)(local_res18 - 8);
            iVar2 = *piVar1;
            *piVar1 = *piVar1 + -1;
            UNLOCK();
            if (iVar2 + -1 < 1) {
              (**(code **)(**(longlong **)(local_res18 - 0x18) + 8))();
            }
            return 0;
          }
          lVar6 = param_1[0x13f];
          sVar8 = strlen("Non-ack resp(1):");
          FUN_140001a34(&local_res18,"Non-ack resp(1):",sVar8 & 0xffffffff);
          uVar11 = uVar14;
          do {
            FUN_140003cf0(&local_res20," %02X",local_d8[uVar11]);
            puVar7 = local_res20;
            FUN_14000d230(&local_res18,local_res20,*(undefined4 *)(local_res20 + -2));
            uVar11 = uVar11 + 1;
          } while ((longlong)uVar11 < 0x20);
          FUN_1400a1378(0);
          FUN_1400a1318();
          FUN_140156b40(&DAT_1405a5df0,local_res18);
          FUN_1400a1378(1);
          do {
            local_d8[0] = '\0';
            DAT_1401f6e4c = 0;
            (**(code **)(*param_1 + 0x110))(param_1,local_d8);
            if (local_d8[0] != 'S') break;
            FUN_140094078(param_1,1,0,local_d8,local_58);
          } while (local_d8[0] == 'S');
          DAT_1401f6e4c = 0x32;
          if ((int)param_1[0x13f] == (int)lVar6) {
            *(int *)(param_1 + 0x13f) = (int)param_1[0x13f] + 1;
          }
          local_96[0xe] = (char)param_1[0x13f];
          local_96[0xf] = (char)((uint)(int)param_1[0x13f] >> 8);
          lVar6 = 0xe;
          do {
            local_96[lVar6] = local_96[lVar6] ^ *(byte *)(lVar6 + 0x9b5 + (longlong)param_1);
            lVar6 = lVar6 + 1;
          } while (lVar6 < 0x10);
          FUN_140003cf0(&local_res18,"Resending with PC=%d");
          FUN_1400a1378(0);
          FUN_1400a1318();
          FUN_140156b40(&DAT_1405a5df0,local_res18);
          FUN_1400a1378(1);
          (**(code **)(*param_1 + 0x108))(param_1,&local_98);
          iVar2 = (**(code **)(*param_1 + 0x110))(param_1,local_d8);
          if (iVar2 < 0) {
            FUN_1400a1378(0);
            FUN_1400a1318();
            FUN_140156b40(&DAT_1405a5df0,"No response");
            FUN_1400a1378(1);
            LOCK();
            piVar1 = (int *)(puVar7 + -1);
            iVar2 = *piVar1;
            *piVar1 = *piVar1 + -1;
            UNLOCK();
            if (iVar2 + -1 < 1) {
              (**(code **)(*(longlong *)puVar7[-3] + 8))();
            }
            LOCK();
            piVar1 = (int *)(local_res18 - 8);
            iVar2 = *piVar1;
            *piVar1 = *piVar1 + -1;
            UNLOCK();
            if (iVar2 + -1 < 1) {
              (**(code **)(**(longlong **)(local_res18 - 0x18) + 8))();
            }
            return 0;
          }
          if (local_d8[0] == -2) {
            *(int *)(param_1 + 0x13f) = (int)param_1[0x13f] + 1;
            if (0xffff < (int)param_1[0x13f]) {
              *(undefined4 *)(param_1 + 0x13f) = 0;
            }
            FUN_1400a1378(0);
            FUN_1400a1318();
            FUN_140156b40(&DAT_1405a5df0,"Resend success");
            FUN_1400a1378(1);
            LOCK();
            piVar1 = (int *)(puVar7 + -1);
            iVar2 = *piVar1;
            *piVar1 = *piVar1 + -1;
            UNLOCK();
            if (iVar2 + -1 < 1) {
              (**(code **)(*(longlong *)puVar7[-3] + 8))();
            }
            LOCK();
            piVar1 = (int *)(local_res18 - 8);
            iVar2 = *piVar1;
            *piVar1 = *piVar1 + -1;
            UNLOCK();
            if (iVar2 + -1 < 1) {
              (**(code **)(**(longlong **)(local_res18 - 0x18) + 8))();
            }
            return 1;
          }
          uVar13 = (ulonglong)((int)uVar13 + 1);
        } while( true );
      }
      puVar15 = local_58;
      FUN_140094078(param_1,1,0,local_d8,puVar15);
      uVar3 = (undefined4)((ulonglong)puVar15 >> 0x20);
      local_d8[0] = '\0';
      iVar2 = (**(code **)(*param_1 + 0x110))(param_1,local_d8);
    } while (-1 < iVar2);
    FUN_1400a1378(0);
    FUN_1400a1318();
    FUN_140156b40(&DAT_1405a5df0,"HC::SCM -3");
    FUN_1400a1378(1);
    FUN_1401215b8(&DAT_140701ec0,0,0x12,0xfffffff6);
    LOCK();
    piVar1 = (int *)(puVar7 + 2);
    iVar2 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar2 + -1 < 1) {
      (**(code **)(*(longlong *)*puVar7 + 8))();
    }
    LOCK();
    piVar1 = (int *)(local_res18 - 8);
    iVar2 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar2 + -1 < 1) {
      (**(code **)(**(longlong **)(local_res18 - 0x18) + 8))();
    }
  }
  return 0;
}


```

- **XREF from non-function address:** `14008110b`

### String `"\"**** Address %02X-%02X CAN Error %02X in SendCANMsg()\""` at `1401acfb8`
- **XREF from:** `1400925f7` in Function **`FUN_140092120`** (`140092120`)
- **XREF from non-function address:** `140081214`

### String `"\"HC::CAN_OpenController16 -1\""` at `1401ad078`
- **XREF from:** `140081c04` in Function **`FUN_140081b64`** (`140081b64`)

```c
// Function: FUN_140081b64 @ 140081b64

undefined8 FUN_140081b64(undefined8 param_1)

{
  byte bVar1;
  int iVar2;
  int iVar3;
  undefined8 uVar4;
  char *pcVar5;
  undefined1 local_res10 [8];
  
  bVar1 = (*DAT_14018c730)();
  if ((((bVar1 & 7) == 7) && (DAT_140702491 != '\0')) && (DAT_140702384 != '\0')) {
    DAT_14070237e = 1;
    FUN_140060500(0x17);
  }
  if (DAT_1401f6814 != 2) {
    iVar2 = DAT_1401f6814;
    iVar3 = FUN_140061688(DAT_1401f6818);
    if (iVar2 == 0) {
      iVar2 = FUN_1400600f4(iVar3,1);
      if (0 < iVar2) {
        return 1;
      }
      pcVar5 = "HC::CAN_OpenController16 -1";
    }
    else {
      if (iVar2 != 3) {
        FUN_14006173c(1);
      }
      if (iVar3 < 0) {
        return 0xfffffffe;
      }
      iVar2 = FUN_14005faf8(iVar3,1);
      if (0 < iVar2) {
        FUN_1400a143c(0x32);
        return 1;
      }
      pcVar5 = "HC::CAN_OpenController -1";
    }
    uVar4 = FUN_140001790(local_res10,pcVar5);
    FUN_140098d5c(param_1,uVar4);
    return 0xffffffff;
  }
  iVar2 = FUN_140060500();
  if (iVar2 != 1) {
    DAT_14025c4dc = 1;
    iVar2 = FUN_140060500(DAT_1401f6818);
    if (iVar2 != 1) {
      DAT_14025c4dc = 0;
      return 0xffffffff;
    }
  }
  return 1;
}


```


### String `"\"HC::CAN_OpenController -1\""` at `1401ad098`
- **XREF from:** `140081c49` in Function **`FUN_140081b64`** (`140081b64`)

### String `"\"HC::CANOpenCtrl:KMode failed 1\""` at `1401ad0b8`
- **XREF from:** `140081c90` in Function **`FUN_140081c70`** (`140081c70`)

```c
// Function: FUN_140081c70 @ 140081c70

undefined8 FUN_140081c70(longlong *param_1)

{
  char cVar1;
  byte bVar2;
  int iVar3;
  undefined8 uVar4;
  bool bVar5;
  undefined1 local_res10 [24];
  
  iVar3 = FUN_1400800d4();
  if (iVar3 < 0) {
    iVar3 = (**(code **)(*param_1 + 0x68))();
    if (iVar3 != 1) {
      uVar4 = FUN_140001790(local_res10,"HC::CANOpenCtrl:KMode failed 1");
      FUN_140098d5c(param_1,uVar4);
    }
    uVar4 = FUN_140001790(local_res10,"HC::CANOpenCtrl -1");
    FUN_140098d5c(param_1,uVar4);
  }
  else {
    iVar3 = FUN_140080270(param_1,1);
    if ((iVar3 < 0) && (iVar3 = FUN_140080270(param_1,1), iVar3 < 0)) {
      iVar3 = (**(code **)(*param_1 + 0x68))(param_1);
      if (iVar3 != 1) {
        uVar4 = FUN_140001790(local_res10,"HC::CANOpenCtrl:KMode failed 2");
        FUN_140098d5c(param_1,uVar4);
      }
      uVar4 = FUN_140001790(local_res10,"HC::CANOpenCtrl -2");
      FUN_140098d5c(param_1,uVar4);
      return 0xfffffffe;
    }
    *(undefined4 *)(param_1 + 0x139) = 0;
    DAT_1405a554c = 2;
    iVar3 = FUN_140081b64(param_1);
    if (iVar3 < 1) {
      DAT_1405a554c = 0xffffffff;
      iVar3 = (**(code **)(*param_1 + 0x68))(param_1);
      if (iVar3 != 1) {
        uVar4 = FUN_140001790(local_res10,"HC::CANOpenCtrl:KMode failed 3");
        FUN_140098d5c(param_1,uVar4);
      }
      uVar4 = FUN_140001790(local_res10,"HC::CANOpenCtrl -3");
      FUN_140098d5c(param_1,uVar4);
      return 0xfffffffd;
    }
    bVar5 = DAT_1401f6814 != 0;
    *(undefined4 *)(param_1 + 2) = 0x55;
    if (bVar5) {
      DAT_14062fb4c = 0xef;
      DAT_14062fb4d = 0x8f;
    }
    *(undefined1 *)((longlong)param_1 + 0x9ce) = 0;
    DAT_1405a554c = 2;
    if (((DAT_140702384 == '\0') || (cVar1 = FUN_140118378(&DAT_140701ec0), cVar1 == '\0')) ||
       (bVar2 = (*DAT_14018c730)(), (bVar2 & 7) != 7)) {
      return 0;
    }
  }
  return 0xffffffff;
}


```

- **XREF from non-function address:** `1400830fb`

### String `"\"HC::CANOpenCtrl -1\""` at `1401ad0d8`
- **XREF from:** `140081cac` in Function **`FUN_140081c70`** (`140081c70`)
- **XREF from non-function address:** `140083117`

### String `"\"HC::CANOpenCtrl:KMode failed 2\""` at `1401ad0f0`
- **XREF from:** `140081cf7` in Function **`FUN_140081c70`** (`140081c70`)
- **XREF from non-function address:** `140083162`

### String `"\"HC::CANOpenCtrl -2\""` at `1401ad110`
- **XREF from:** `140081d13` in Function **`FUN_140081c70`** (`140081c70`)
- **XREF from non-function address:** `14008317e`

### String `"\"HC::CANOpenCtrl:KMode failed 3\""` at `1401ad128`
- **XREF from:** `140081d6c` in Function **`FUN_140081c70`** (`140081c70`)

### String `"\"HC::CANOpenCtrl -3\""` at `1401ad148`
- **XREF from:** `140081d88` in Function **`FUN_140081c70`** (`140081c70`)

### String `"\"HC::FastInit -1\""` at `1401ad1e0`
- **XREF from non-function address:** `140082123`

### String `"\"HC::FastInit -2\""` at `1401ad1f0`
- **XREF from non-function address:** `140082168`

### String `"\"HC::Echo10400 -1\""` at `1401ad200`
- **XREF from non-function address:** `140082242`

### String `"\"HC::Echo10400 -2\""` at `1401ad218`
- **XREF from non-function address:** `14008226d`

### String `"\"HC::SetBoot -1\""` at `1401ad238`
- **XREF from:** `14008324f` in Function **`FUN_140083208`** (`140083208`)

```c
// Function: FUN_140083208 @ 140083208

undefined8 FUN_140083208(longlong *param_1,undefined1 param_2)

{
  int iVar1;
  undefined8 uVar2;
  undefined1 local_res8 [32];
  char local_108 [256];
  
  local_108[0] = '\x02';
  local_108[1] = 0xe;
  local_108[2] = param_2;
  (**(code **)(*param_1 + 0x108))(param_1,local_108);
  DAT_1401f6e4c = 0x2ee;
  iVar1 = (**(code **)(*param_1 + 0x110))(param_1,local_108);
  if (iVar1 < 0) {
    uVar2 = FUN_140001790(local_res8,"HC::SetBoot -1");
    FUN_140098d5c(param_1,uVar2);
    uVar2 = 0xffffffff;
  }
  else if (local_108[0] == -2) {
    FUN_1400832b4(param_1);
    uVar2 = 0;
  }
  else {
    uVar2 = FUN_140001790(local_res8,"HC::SetBoot -2");
    FUN_140098d5c(param_1,uVar2);
    uVar2 = 0xfffffffe;
  }
  return uVar2;
}


```


### String `"\"HC::SetBoot -2\""` at `1401ad248`
- **XREF from:** `14008327a` in Function **`FUN_140083208`** (`140083208`)

### String `"\"HC::ReadBoot -1\""` at `1401ad258`
- **XREF from:** `1400832f7` in Function **`FUN_1400832b4`** (`1400832b4`)

```c
// Function: FUN_1400832b4 @ 1400832b4

ulonglong FUN_1400832b4(longlong *param_1)

{
  int iVar1;
  undefined8 uVar2;
  ulonglong uVar3;
  undefined1 local_res8 [32];
  char local_108;
  byte local_107;
  
  local_108 = '\x01';
  local_107 = 0xd;
  (**(code **)(*param_1 + 0x108))(param_1,&local_108);
  DAT_1401f6e4c = 0x2ee;
  iVar1 = (**(code **)(*param_1 + 0x110))(param_1,&local_108);
  if (iVar1 < 0) {
    uVar2 = FUN_140001790(local_res8,"HC::ReadBoot -1");
    FUN_140098d5c(param_1,uVar2);
    uVar3 = 0xffffffff;
  }
  else if (local_108 == '\r') {
    uVar3 = (ulonglong)local_107;
  }
  else {
    uVar2 = FUN_140001790(local_res8,"HC::ReadBoot -2");
    FUN_140098d5c(param_1,uVar2);
    uVar3 = 0xfffffffe;
  }
  return uVar3;
}


```


### String `"\"HC::ReadBoot -2\""` at `1401ad268`
- **XREF from:** `140083322` in Function **`FUN_1400832b4`** (`1400832b4`)

### String `"\"HC::GC -1\""` at `1401ad278`
- **XREF from:** `1400833be` in Function **`FUN_140083358`** (`140083358`)

```c
// Function: FUN_140083358 @ 140083358

ulonglong FUN_140083358(longlong *param_1)

{
  int iVar1;
  undefined8 uVar2;
  ulonglong uVar3;
  byte *pbVar4;
  byte bVar5;
  longlong lVar6;
  undefined1 local_res8 [8];
  byte local_108 [256];
  
  uVar3 = 0;
  if (*(char *)((longlong)param_1 + 0x4a) == '\0') {
    bVar5 = 0x16;
    local_108[0] = 1;
  }
  else {
    bVar5 = 0x19;
    local_108[0] = 2;
    local_108[2] = 0;
  }
  local_108[1] = bVar5;
  (**(code **)(*param_1 + 0x108))(param_1,local_108);
  DAT_1401f6e4c = 0x2ee;
  iVar1 = (**(code **)(*param_1 + 0x110))(param_1,local_108);
  if (iVar1 < 0) {
    uVar2 = FUN_140001790(local_res8,"HC::GC -1");
    FUN_140098d5c(param_1,uVar2);
    uVar3 = 0xffffffffffffffff;
  }
  else if (local_108[0] == bVar5) {
    lVar6 = 4;
    if (*(char *)((longlong)param_1 + 0x4a) == '\0') {
      iVar1 = 4;
    }
    else {
      FUN_14011af34(&DAT_140701ec0,local_108 + 1,0x10,1,1);
      iVar1 = 0xc;
    }
    pbVar4 = local_108 + iVar1;
    do {
      bVar5 = *pbVar4;
      pbVar4 = pbVar4 + -1;
      uVar3 = (ulonglong)((int)uVar3 << 8 | (uint)bVar5);
      lVar6 = lVar6 + -1;
    } while (lVar6 != 0);
  }
  else {
    uVar2 = FUN_140001790(local_res8,"HC::GC -2");
    FUN_140098d5c(param_1,uVar2);
    uVar3 = 0xfffffffffffffffe;
  }
  return uVar3;
}


```


### String `"\"HC::GC -2\""` at `1401ad288`
- **XREF from:** `1400833ed` in Function **`FUN_140083358`** (`140083358`)

### String `"\"HC::GCr -1\""` at `1401ad298`
- **XREF from:** `140083519` in Function **`FUN_14008347c`** (`14008347c`)

```c
// Function: FUN_14008347c @ 14008347c

uint FUN_14008347c(longlong *param_1)

{
  byte bVar1;
  int iVar2;
  undefined8 uVar3;
  byte *pbVar4;
  uint uVar5;
  longlong lVar6;
  undefined1 local_res8 [8];
  char local_108 [4];
  byte local_104 [252];
  
  uVar5 = 0;
  local_108[0] = '\x01';
  local_108[1] = 0x17;
  (**(code **)(*param_1 + 0x108))(param_1,local_108);
  DAT_1401f6e4c = 0x2ee;
  iVar2 = (**(code **)(*param_1 + 0x110))(param_1,local_108);
  if (iVar2 < 0) {
    FUN_140111ed0(DAT_1405a55e4,0x4b00);
    (**(code **)(*param_1 + 0x68))(param_1);
    local_108[0] = '\x01';
    local_108[1] = 0x17;
    (**(code **)(*param_1 + 0x108))(param_1,local_108);
    DAT_1401f6e4c = 0x2ee;
    iVar2 = (**(code **)(*param_1 + 0x110))(param_1,local_108);
    if (iVar2 < 0) {
      uVar3 = FUN_140001790(local_res8,"HC::GCr -1");
      FUN_140098d5c(param_1,uVar3);
      return 0xffffffff;
    }
  }
  else if (local_108[0] != '\x17') {
    uVar3 = FUN_140001790(local_res8,"HC::GCr -2");
    FUN_140098d5c(param_1,uVar3);
    return 0xfffffffe;
  }
  pbVar4 = local_104;
  lVar6 = 4;
  do {
    bVar1 = *pbVar4;
    pbVar4 = pbVar4 + -1;
    uVar5 = uVar5 << 8 | (uint)bVar1;
    lVar6 = lVar6 + -1;
  } while (lVar6 != 0);
  return uVar5;
}


```


### String `"\"HC::GCr -2\""` at `1401ad2a8`
- **XREF from:** `140083544` in Function **`FUN_14008347c`** (`14008347c`)
- **XREF from:** `140083634` in Function **`FUN_14008359c`** (`14008359c`)

```c
// Function: FUN_14008359c @ 14008359c

undefined1 FUN_14008359c(longlong *param_1,undefined4 param_2)

{
  int iVar1;
  undefined8 uVar2;
  char *pcVar3;
  undefined1 local_res8 [8];
  char local_108 [3];
  undefined1 local_105;
  undefined1 local_104;
  undefined1 local_103;
  
  local_105 = (undefined1)((uint)param_2 >> 8);
  local_108[2] = (char)param_2;
  local_108[0] = '\x05';
  local_103 = (undefined1)((uint)param_2 >> 0x18);
  local_104 = (undefined1)((uint)param_2 >> 0x10);
  local_108[1] = 0x18;
  (**(code **)(*param_1 + 0x108))(param_1,local_108);
  DAT_1401f6e4c = 0x2ee;
  iVar1 = (**(code **)(*param_1 + 0x110))(param_1);
  if (iVar1 < 0) {
    AfxMessageBox("HC::SCr -1",0,0);
    pcVar3 = "HC::SCr -1";
  }
  else {
    if (local_108[0] == -2) {
      return 1;
    }
    AfxMessageBox("HC::SCr -2",0,0);
    pcVar3 = "HC::GCr -2";
  }
  uVar2 = FUN_140001790(local_res8,pcVar3);
  FUN_140098d5c(param_1,uVar2);
  return 0;
}


```


### String `"\"HC::SCr -1\""` at `1401ad2b8`
- **XREF from:** `140083602` in Function **`FUN_14008359c`** (`14008359c`)
- **XREF from:** `140083613` in Function **`FUN_14008359c`** (`14008359c`)

### String `"\"HC::SCr -2\""` at `1401ad2c8`
- **XREF from:** `140083623` in Function **`FUN_14008359c`** (`14008359c`)

### String `"\"HC::GCp -1\""` at `1401ad2e8`
- **XREF from:** `140083c37` in Function **`FUN_140083ba4`** (`140083ba4`)

```c
// Function: FUN_140083ba4 @ 140083ba4

uint FUN_140083ba4(longlong *param_1)

{
  int *piVar1;
  char cVar2;
  int iVar3;
  int iVar4;
  longlong *plVar5;
  undefined8 *puVar6;
  undefined8 uVar7;
  size_t sVar8;
  char *pcVar9;
  uint uVar10;
  undefined8 *local_res20;
  longlong local_138;
  undefined1 local_130 [8];
  undefined1 local_128 [8];
  undefined1 local_120 [8];
  undefined8 local_118;
  char acStack_10c [260];
  
  local_118 = 0xfffffffffffffffe;
  plVar5 = (longlong *)FUN_14013a630();
  if (plVar5 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  puVar6 = (undefined8 *)(**(code **)(*plVar5 + 0x18))(plVar5);
  local_res20 = puVar6 + 3;
  acStack_10c[4] = '\x02';
  acStack_10c[5] = 0xd5;
  acStack_10c[6] = 4;
  (**(code **)(*param_1 + 0x108))(param_1,acStack_10c + 4);
  DAT_1401f6e4c = 0x2ee;
  iVar3 = (**(code **)(*param_1 + 0x110))(param_1,acStack_10c + 4);
  if (iVar3 < 0) {
    uVar7 = FUN_140001790(local_130,"HC::GCp -1");
    FUN_140098d5c(param_1,uVar7);
    LOCK();
    piVar1 = (int *)(puVar6 + 2);
    iVar3 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar3 + -1 < 1) {
      (**(code **)(*(longlong *)*puVar6 + 8))();
    }
    uVar10 = 0xffffffff;
  }
  else if (acStack_10c[4] == -2) {
    uVar10 = 0;
    pcVar9 = acStack_10c + 4;
    iVar3 = 0;
    while( true ) {
      cVar2 = FUN_1400a11a8(0);
      *pcVar9 = cVar2;
      pcVar9 = pcVar9 + 1;
      if (cVar2 == -100) break;
      uVar10 = uVar10 ^ (int)cVar2;
      iVar3 = iVar3 + 1;
    }
    sVar8 = strlen(acStack_10c + 4);
    FUN_140001a34(&local_res20,acStack_10c + 4,sVar8 & 0xffffffff);
    FUN_14004d0dc(&local_res20,10);
    FUN_14004d0dc(&local_res20,0xd);
    sVar8 = strlen("\n  ");
    FUN_14000d230(param_1 + 5,&DAT_1401ad304,sVar8 & 0xffffffff);
    FUN_14000d230(param_1 + 5,local_res20,*(undefined4 *)(local_res20 + -2));
    acStack_10c[iVar3 + 1] = '\0';
    DAT_14070248c = uVar10;
    uVar7 = FUN_140001790(local_120,"Clszqjdkw#+`*#1327#az#Kf{#Nj`qlpzpwfnp#Swz#%#Qlpp.Wf`k/#OO@-");
    puVar6 = (undefined8 *)FUN_14011aa2c(&DAT_140701ec0,&local_138,uVar7);
    iVar4 = strcmp(acStack_10c + 4,(char *)*puVar6);
    LOCK();
    piVar1 = (int *)(local_138 + -8);
    iVar3 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar3 + -1 < 1) {
      (**(code **)(**(longlong **)(local_138 + -0x18) + 8))();
    }
    if (iVar4 == 0) {
      LOCK();
      piVar1 = (int *)(local_res20 + -1);
      iVar3 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (iVar3 + -1 < 1) {
        (**(code **)(*(longlong *)local_res20[-3] + 8))();
      }
    }
    else {
      LOCK();
      piVar1 = (int *)(local_res20 + -1);
      iVar3 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (iVar3 + -1 < 1) {
        (**(code **)(*(longlong *)local_res20[-3] + 8))();
      }
      uVar10 = 0;
    }
  }
  else {
    uVar7 = FUN_140001790(local_128,"HC::GCp -2");
    FUN_140098d5c(param_1,uVar7);
    LOCK();
    piVar1 = (int *)(puVar6 + 2);
    iVar3 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar3 + -1 < 1) {
      (**(code **)(*(longlong *)*puVar6 + 8))();
    }
    uVar10 = 0xfffffffe;
  }
  return uVar10;
}


```


### String `"\"HC::GCp -2\""` at `1401ad2f8`
- **XREF from:** `140083c80` in Function **`FUN_140083ba4`** (`140083ba4`)

### String `"\"HC::SCM-O -2\""` at `1401ad388`
- **XREF from:** `14008422b` in Function **`FUN_140083fc0`** (`140083fc0`)

```c
// Function: FUN_140083fc0 @ 140083fc0

uint FUN_140083fc0(longlong param_1,char param_2,char param_3,byte *param_4,uint *param_5)

{
  int *piVar1;
  byte bVar2;
  byte bVar3;
  byte bVar4;
  byte bVar5;
  longlong *plVar6;
  longlong lVar7;
  size_t sVar8;
  uint uVar9;
  byte *pbVar10;
  longlong lVar11;
  int iVar12;
  byte *pbVar13;
  undefined1 *puVar14;
  char *local_88;
  longlong local_80 [3];
  char local_68 [64];
  
  local_80[1] = 0xfffffffffffffffe;
  plVar6 = (longlong *)FUN_14013a630();
  if (plVar6 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  lVar7 = (**(code **)(*plVar6 + 0x18))(plVar6);
  local_88 = (char *)(lVar7 + 0x18);
  plVar6 = (longlong *)FUN_14013a630();
  if (plVar6 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  lVar7 = (**(code **)(*plVar6 + 0x18))(plVar6);
  lVar7 = lVar7 + 0x18;
  local_80[0] = lVar7;
  strncpy(local_68,(char *)param_4,0x40);
  pbVar10 = (byte *)(param_1 + 0x89cf);
  lVar11 = 0x10;
  pbVar13 = param_4;
  do {
    pbVar13 = pbVar13 + 1;
    *pbVar13 = *pbVar13 ^ *pbVar10;
    pbVar10 = pbVar10 + 1;
    lVar11 = lVar11 + -1;
  } while (lVar11 != 0);
  bVar2 = param_4[6];
  *(byte *)((longlong)param_5 + 5) = bVar2 & 0xf;
  iVar12 = 0;
  if ((bVar2 & 0xf) != 0) {
    pbVar10 = (byte *)((longlong)param_5 + 6);
    pbVar13 = param_4 + 7;
    do {
      *pbVar10 = *pbVar13;
      iVar12 = iVar12 + 1;
      pbVar13 = pbVar13 + 1;
      pbVar10 = pbVar10 + 1;
    } while (iVar12 < (int)(uint)*(byte *)((longlong)param_5 + 5));
  }
  bVar2 = param_4[2];
  bVar3 = param_4[3];
  bVar4 = param_4[4];
  bVar5 = param_4[5];
  if ((bVar3 & 8) == 0) {
    *(undefined1 *)(param_5 + 1) = 0;
  }
  else {
    *(undefined1 *)(param_5 + 1) = 1;
  }
  uVar9 = (uint)bVar2 << 3 | (uint)(bVar3 >> 5);
  if ((char)param_5[1] == '\x01') {
    *param_5 = uVar9 << 0x12 | ((bVar3 & 2) << 8 | (uint)bVar4) << 8 | (uint)bVar5;
  }
  else {
    *param_5 = uVar9;
  }
  uVar9 = (uint)param_4[0x10] * 0x100 + (uint)param_4[0xf];
  if (param_2 == '\0') {
    if (uVar9 != *(uint *)(param_1 + 0x8a08)) {
      if (param_3 != '\0') goto LAB_140084347;
      sVar8 = strlen("Communication error");
      FUN_140001a34(&local_88,"Communication error",sVar8 & 0xffffffff);
      FUN_140003cf0(local_80,"%s: %d vs %d",local_88,uVar9,*(undefined4 *)(param_1 + 0x8a08));
      FUN_1400a1318();
      lVar7 = local_80[0];
      FUN_140156b40(&DAT_1405a5df0);
      FUN_1400a1378(1);
      AfxMessageBox(local_88,0,0);
      DAT_140631e44 = 1;
    }
  }
  else {
    FUN_140003cf0(&local_88,"Out-of-synch RX Packet with ID: %03X stacked in pos.%d, PC %d:",
                  *param_5,*(undefined4 *)(param_1 + 0x9c8),uVar9);
    iVar12 = 0;
    if (*(char *)((longlong)param_5 + 5) != '\0') {
      puVar14 = (undefined1 *)((longlong)param_5 + 6);
      do {
        FUN_140003cf0(local_80," %02X",*puVar14);
        lVar7 = local_80[0];
        FUN_14000d230(&local_88,local_80[0],*(undefined4 *)(local_80[0] + -0x10));
        iVar12 = iVar12 + 1;
        puVar14 = puVar14 + 1;
      } while (iVar12 < (int)(uint)*(byte *)((longlong)param_5 + 5));
    }
    FUN_1400a1378(0);
    FUN_1400a1318();
    FUN_140156b40(&DAT_1405a5df0,local_88);
    FUN_1400a1378(1);
    uVar9 = uVar9 + 1;
    *(uint *)(param_1 + 0x8a08) = uVar9;
    if (0xffff < uVar9) {
      *(undefined4 *)(param_1 + 0x8a08) = 0;
    }
    iVar12 = *(int *)(param_1 + 0x9c8);
    *(int *)(param_1 + 0x9c8) = iVar12 + 1;
    memcpy((void *)((longlong)iVar12 * 0x40 + 0x1c8 + param_1),local_68,0x40);
    if (0x1e < *(int *)(param_1 + 0x9c8)) {
      FUN_1400a1378(0);
      FUN_1400a1318();
      FUN_140156b40(&DAT_1405a5df0,"HC::SCM-O -2");
      FUN_1400a1378(1);
      *(undefined4 *)(param_1 + 0x9c8) = 0;
      LOCK();
      piVar1 = (int *)(lVar7 + -8);
      iVar12 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (iVar12 + -1 < 1) {
        (**(code **)(**(longlong **)(lVar7 + -0x18) + 8))();
      }
      LOCK();
      piVar1 = (int *)(local_88 + -8);
      iVar12 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (0 < iVar12 + -1) {
        return uVar9;
      }
      (**(code **)(**(longlong **)(local_88 + -0x18) + 8))();
      return uVar9;
    }
  }
  if (((param_3 == '\0') && (param_2 == '\0')) &&
     (*(int *)(param_1 + 0x8a08) = *(int *)(param_1 + 0x8a08) + 1,
     0xffff < *(int *)(param_1 + 0x8a08))) {
    *(undefined4 *)(param_1 + 0x8a08) = 0;
  }
LAB_140084347:
  LOCK();
  piVar1 = (int *)(lVar7 + -8);
  iVar12 = *piVar1;
  *piVar1 = *piVar1 + -1;
  UNLOCK();
  if (iVar12 + -1 < 1) {
    (**(code **)(**(longlong **)(lVar7 + -0x18) + 8))();
  }
  LOCK();
  piVar1 = (int *)(local_88 + -8);
  iVar12 = *piVar1;
  *piVar1 = *piVar1 + -1;
  UNLOCK();
  if (iVar12 + -1 < 1) {
    (**(code **)(**(longlong **)(local_88 + -0x18) + 8))();
  }
  return uVar9;
}


```

- **XREF from:** `1400942ea` in Function **`FUN_140094078`** (`140094078`)

```c
// Function: FUN_140094078 @ 140094078

uint FUN_140094078(longlong param_1,char param_2,char param_3,byte *param_4,uint *param_5)

{
  int *piVar1;
  byte bVar2;
  longlong *plVar3;
  longlong lVar4;
  size_t sVar5;
  byte *pbVar6;
  longlong lVar7;
  uint uVar8;
  int iVar9;
  uint uVar10;
  byte *pbVar11;
  undefined1 *puVar12;
  byte bStack_87;
  char *local_80;
  longlong local_78 [2];
  char local_68 [64];
  
  local_78[1] = 0xfffffffffffffffe;
  plVar3 = (longlong *)FUN_14013a630();
  if (plVar3 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  lVar4 = (**(code **)(*plVar3 + 0x18))(plVar3);
  local_80 = (char *)(lVar4 + 0x18);
  plVar3 = (longlong *)FUN_14013a630();
  if (plVar3 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  lVar4 = (**(code **)(*plVar3 + 0x18))(plVar3);
  lVar4 = lVar4 + 0x18;
  local_78[0] = lVar4;
  strncpy(local_68,(char *)param_4,0x40);
  pbVar6 = (byte *)(param_1 + 0x9b5);
  lVar7 = 0x10;
  pbVar11 = param_4;
  do {
    pbVar11 = pbVar11 + 1;
    *pbVar11 = *pbVar11 ^ *pbVar6;
    pbVar6 = pbVar6 + 1;
    lVar7 = lVar7 + -1;
  } while (lVar7 != 0);
  bVar2 = param_4[6];
  *(byte *)((longlong)param_5 + 5) = bVar2 & 0xf;
  iVar9 = 0;
  if ((bVar2 & 0xf) != 0) {
    pbVar6 = (byte *)((longlong)param_5 + 6);
    pbVar11 = param_4 + 7;
    do {
      *pbVar6 = *pbVar11;
      iVar9 = iVar9 + 1;
      pbVar11 = pbVar11 + 1;
      pbVar6 = pbVar6 + 1;
    } while (iVar9 < (int)(uint)*(byte *)((longlong)param_5 + 5));
  }
  uVar8 = *(uint *)(param_4 + 2);
  if ((uVar8 & 0x80000000) == 0) {
    *(undefined1 *)(param_5 + 1) = 0;
  }
  else {
    *(undefined1 *)(param_5 + 1) = 1;
  }
  bStack_87 = (byte)(uVar8 >> 8);
  uVar10 = (bStack_87 & 7) << 8 | uVar8 & 0xff;
  if ((char)param_5[1] == '\x01') {
    *param_5 = uVar10 << 0x12 |
               ((uVar8 >> 0x18 & 0x1f) << 8 | uVar8 >> 0x10 & 0xff) << 5 | (uint)(bStack_87 >> 3);
  }
  else {
    *param_5 = uVar10;
  }
  uVar8 = (uint)param_4[0x10] * 0x100 + (uint)param_4[0xf];
  if (param_2 == '\0') {
    if (uVar8 != *(uint *)(param_1 + 0x9f8)) {
      if (param_3 != '\0') goto LAB_140094406;
      sVar5 = strlen("Communication error");
      FUN_140001a34(&local_80,"Communication error",sVar5 & 0xffffffff);
      FUN_140003cf0(local_78,"%s: %d vs %d",local_80,uVar8,*(undefined4 *)(param_1 + 0x9f8));
      FUN_1400a1318();
      lVar4 = local_78[0];
      FUN_140156b40(&DAT_1405a5df0);
      FUN_1400a1378(1);
      AfxMessageBox(local_80,0,0);
      DAT_140631e44 = 1;
    }
  }
  else {
    FUN_140003cf0(&local_80,"Out-of-synch RX Packet with ID: %03X stacked in pos.%d, PC %d:",
                  *param_5,*(undefined4 *)(param_1 + 0x1b0),uVar8);
    iVar9 = 0;
    if (*(char *)((longlong)param_5 + 5) != '\0') {
      puVar12 = (undefined1 *)((longlong)param_5 + 6);
      do {
        FUN_140003cf0(local_78," %02X",*puVar12);
        lVar4 = local_78[0];
        FUN_14000d230(&local_80,local_78[0],*(undefined4 *)(local_78[0] + -0x10));
        iVar9 = iVar9 + 1;
        puVar12 = puVar12 + 1;
      } while (iVar9 < (int)(uint)*(byte *)((longlong)param_5 + 5));
    }
    FUN_1400a1378(0);
    FUN_1400a1318();
    FUN_140156b40(&DAT_1405a5df0,local_80);
    FUN_1400a1378(1);
    uVar8 = uVar8 + 1;
    *(uint *)(param_1 + 0x9f8) = uVar8;
    if (0xffff < uVar8) {
      *(undefined4 *)(param_1 + 0x9f8) = 0;
    }
    iVar9 = *(int *)(param_1 + 0x1b0);
    *(int *)(param_1 + 0x1b0) = iVar9 + 1;
    memcpy((void *)((longlong)iVar9 * 0x40 + 0x1b4 + param_1),local_68,0x40);
    if (0x1e < *(int *)(param_1 + 0x1b0)) {
      FUN_1400a1378(0);
      FUN_1400a1318();
      FUN_140156b40(&DAT_1405a5df0,"HC::SCM-O -2");
      FUN_1400a1378(1);
      *(undefined4 *)(param_1 + 0x1b0) = 0;
      LOCK();
      piVar1 = (int *)(lVar4 + -8);
      iVar9 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (iVar9 + -1 < 1) {
        (**(code **)(**(longlong **)(lVar4 + -0x18) + 8))();
      }
      LOCK();
      piVar1 = (int *)(local_80 + -8);
      iVar9 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (0 < iVar9 + -1) {
        return uVar8;
      }
      (**(code **)(**(longlong **)(local_80 + -0x18) + 8))();
      return uVar8;
    }
  }
  if (((param_3 == '\0') && (param_2 == '\0')) &&
     (*(int *)(param_1 + 0x9f8) = *(int *)(param_1 + 0x9f8) + 1, 0xffff < *(int *)(param_1 + 0x9f8))
     ) {
    *(undefined4 *)(param_1 + 0x9f8) = 0;
  }
LAB_140094406:
  LOCK();
  piVar1 = (int *)(lVar4 + -8);
  iVar9 = *piVar1;
  *piVar1 = *piVar1 + -1;
  UNLOCK();
  if (iVar9 + -1 < 1) {
    (**(code **)(**(longlong **)(lVar4 + -0x18) + 8))();
  }
  LOCK();
  piVar1 = (int *)(local_80 + -8);
  iVar9 = *piVar1;
  *piVar1 = *piVar1 + -1;
  UNLOCK();
  if (iVar9 + -1 < 1) {
    (**(code **)(**(longlong **)(local_80 + -0x18) + 8))();
  }
  return uVar8;
}


```


### String `"\"\n\nPLEASE NOTE:\nThis specific update is only available\n  through VCDS-Mobile (web interface).\nYou WILL NOT be able to use the USB utility.\nFor more info, join or visit forums.ross-tech.com .\n\""` at `1401ad4e0`
- **XREF from non-function address:** `1400850b2`
- **XREF from non-function address:** `1400850c1`

### String `"\"0x17: CANbits=0x%02X, is8E=%d, isNot8E=%d, tmp=0x%02X\""` at `1401ad610`
- **XREF from non-function address:** `140085a14`

### String `"\"Open CAN controller %02X\""` at `1401ad6c8`
- **XREF from:** `1400861da` in Function **`FUN_14008605c`** (`14008605c`)

```c
// Function: FUN_14008605c @ 14008605c

/* WARNING: Globals starting with '_' overlap smaller symbols at the same address */

undefined8 FUN_14008605c(longlong *param_1,char param_2,int param_3,int param_4)

{
  int *piVar1;
  short sVar2;
  char cVar3;
  uint uVar4;
  int iVar5;
  longlong *plVar6;
  undefined8 *puVar7;
  longlong lVar8;
  size_t sVar9;
  undefined8 uVar10;
  byte *pbVar11;
  uint uVar12;
  longlong lVar13;
  undefined4 uVar14;
  int local_78;
  longlong local_70;
  undefined8 *local_68;
  ulonglong local_60;
  longlong local_58;
  ulonglong local_50;
  undefined8 local_48;
  char local_40 [24];
  
  local_48 = 0xfffffffffffffffe;
  plVar6 = (longlong *)FUN_14013a630();
  lVar13 = 0;
  if (plVar6 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  local_70 = (**(code **)(*plVar6 + 0x18))(plVar6);
  local_70 = local_70 + 0x18;
  plVar6 = (longlong *)FUN_14013a630();
  if (plVar6 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  puVar7 = (undefined8 *)(**(code **)(*plVar6 + 0x18))(plVar6);
  local_68 = puVar7 + 3;
  local_78 = 1;
  DAT_14070229c = 0;
  DAT_140631e44 = 0;
  *(undefined4 *)((longlong)param_1 + 0x4fb4) = 0;
  *(undefined2 *)((longlong)param_1 + 0xab4) = 0;
  *(undefined2 *)(param_1 + 0x157) = 0;
  if (param_3 == 1) {
    if (0x7f < DAT_1401f6818) {
      FUN_1401215b8(&DAT_140701ec0,1,0xc);
      LOCK();
      piVar1 = (int *)(puVar7 + 2);
      iVar5 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (iVar5 + -1 < 1) {
        (**(code **)(*(longlong *)*puVar7 + 8))();
      }
      LOCK();
      piVar1 = (int *)(local_70 + -8);
      iVar5 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (iVar5 + -1 < 1) {
        (**(code **)(**(longlong **)(local_70 + -0x18) + 8))();
      }
      return 0xffffffff;
    }
    FUN_140003cf0(&local_68,"Open K controller %02X");
  }
  else if (param_3 == 2) {
    FUN_140003cf0(&local_68,"Open CAN controller %02X",DAT_1401f6818);
  }
  else {
    if (param_3 != 4) {
      FUN_1401215b8(&DAT_140701ec0,1,0xc,0xfffffff6);
      LOCK();
      piVar1 = (int *)(puVar7 + 2);
      iVar5 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (iVar5 + -1 < 1) {
        (**(code **)(*(longlong *)*puVar7 + 8))();
      }
      LOCK();
      piVar1 = (int *)(local_70 + -8);
      iVar5 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (iVar5 + -1 < 1) {
        (**(code **)(**(longlong **)(local_70 + -0x18) + 8))();
      }
      return 0xffffffff;
    }
    FUN_140003cf0(&local_68,"Open DoIP controller %02X",DAT_1401f6818);
  }
  cVar3 = FUN_14011f1c8(&DAT_140701ec0);
  if (cVar3 == '\0') {
    sVar9 = strlen(", No valid VIN");
    FUN_140001a34(&local_70,", No valid VIN",sVar9 & 0xffffffff);
  }
  else {
    FUN_140003cf0(&local_70,", VIN: %s",DAT_140638220);
  }
  FUN_14000d230(&local_68,local_70,*(undefined4 *)(local_70 + -0x10));
  puVar7 = local_68;
  if (DAT_1405a7338 != 0) {
    FUN_1400a1378(1);
    FUN_1400a1318();
    FUN_140156b40(&DAT_1405a5df0,puVar7);
    FUN_1400a1378(1);
  }
  uVar4 = FUN_14011b248(&DAT_140701ec0);
  *(uint *)((longlong)param_1 + 0x1bc) = uVar4;
  local_60 = (ulonglong)uVar4;
  local_40[0] = (char)DAT_1401f6818;
  uVar14 = 3;
  local_50 = local_60;
  if (param_3 == 2) {
    local_40[1] = 4;
    if (DAT_1401f6818 == 0x33) {
      local_40[2] = 0x20;
    }
    else {
      local_40[2] = (char)param_4;
      if (param_4 == 0xff) {
        local_40[2] = 0x1c;
      }
    }
    local_40[3] = '\0';
    local_40[4] = 0;
    local_40[5] = 0;
  }
  else if (param_3 == 4) {
    if (DAT_1401f6818 == 0x33) {
      LOCK();
      piVar1 = (int *)(puVar7 + -1);
      iVar5 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (iVar5 + -1 < 1) {
        (**(code **)(*(longlong *)puVar7[-3] + 8))();
      }
      LOCK();
      piVar1 = (int *)(local_70 + -8);
      iVar5 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (iVar5 + -1 < 1) {
        (**(code **)(**(longlong **)(local_70 + -0x18) + 8))();
      }
      return 0xffffffff;
    }
    if ((char)param_1[7] == '\0') {
      LOCK();
      piVar1 = (int *)(puVar7 + -1);
      iVar5 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (iVar5 + -1 < 1) {
        (**(code **)(*(longlong *)puVar7[-3] + 8))();
      }
      LOCK();
      piVar1 = (int *)(local_70 + -8);
      iVar5 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (iVar5 + -1 < 1) {
        (**(code **)(**(longlong **)(local_70 + -0x18) + 8))();
      }
      return 0xffffffff;
    }
    local_40[0] = '\0';
    FUN_140086fa4(param_1,0x44,local_40,1);
    FUN_14008734c(param_1,0);
    if (*(short *)((longlong)param_1 + 0x214) == 3) {
      if (param_2 != '\0') {
        FUN_140143988(DAT_140630c70,"DoIP rechk");
      }
      FUN_1400a143c(1000);
      FUN_140086fa4(param_1,0x44,local_40,1);
      FUN_14008734c(param_1,0);
    }
    if (((*(short *)((longlong)param_1 + 0x214) == 0x44) &&
        ((*(byte *)((longlong)param_1 + 0x21a) & 0x10) == 0)) &&
       ((*(byte *)((longlong)param_1 + 0x21a) & 0x20) == 0)) {
      if (param_2 != '\0') {
        FUN_140143988(DAT_140630c70,"DoIP rechk");
      }
      FUN_1400a143c(1000);
      FUN_140086fa4(param_1,0x44,local_40,1);
      FUN_14008734c(param_1,0);
    }
    if ((*(short *)((longlong)param_1 + 0x214) == 0x44) &&
       ((*(byte *)((longlong)param_1 + 0x21a) & 0x10) != 0)) {
      LOCK();
      piVar1 = (int *)(puVar7 + -1);
      iVar5 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (iVar5 + -1 < 1) {
        (**(code **)(*(longlong *)puVar7[-3] + 8))();
      }
      LOCK();
      piVar1 = (int *)(local_70 + -8);
      iVar5 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (iVar5 + -1 < 1) {
        (**(code **)(**(longlong **)(local_70 + -0x18) + 8))();
      }
      return 0xffffffff;
    }
    local_40[0] = (char)DAT_1401f6818;
    local_40[1] = 0x20;
    local_40[2] = 0x10;
    if (param_4 == 0x30) {
      local_40[2] = 0x30;
    }
    local_40[3] = '\0';
    local_40[4] = 0;
    local_40[5] = 0;
  }
  else {
    local_40[1] = (char)param_4;
    if (param_4 == 0xff) {
      local_40[1] = 3;
    }
    local_40[2] = 3;
    uVar4 = 0x5dc;
    uVar12 = 0x9c4;
    if (DAT_1401f6818 == 0x33) {
      uVar4 = 0x1194;
      uVar12 = 0x1964;
    }
    if (DAT_1401f6818 == 2) {
      uVar4 = 2000;
      uVar12 = 3000;
    }
    local_40[3] = (char)(uVar4 / 500) * '\x10' + (char)(uVar12 / 500);
    local_40[4] = 0xaa;
    local_40[5] = (undefined1)DAT_1401f3574;
  }
  local_40[6] = (char)((uint)DAT_1401f6818 >> 8);
  FUN_140086fa4(param_1,2,local_40,7);
  FUN_14008734c(param_1,1);
  DAT_1401f6e4c = 100;
  if (((*(short *)((longlong)param_1 + 0x214) == 1) || (*(short *)((longlong)param_1 + 0x214) != 4))
     || (*(char *)((longlong)param_1 + 0x21a) != '\x06')) {
    FUN_140156be0(local_40,&DAT_1401ad74c);
    sVar2 = *(short *)((longlong)param_1 + 0x214);
    while( true ) {
      if (((sVar2 == 2) || (*(short *)((longlong)param_1 + 0x214) == 4)) ||
         (*(short *)((longlong)param_1 + 0x214) == 3)) goto LAB_140086a40;
      iVar5 = (**(code **)(*param_1 + 0xf8))(param_1,0);
      FUN_14008734c(param_1);
      (*DAT_14018c720)(1);
      if (*(short *)((longlong)param_1 + 0x214) == 7) {
        if ((uint)*(byte *)((longlong)param_1 + 0x21a) * 0x100 +
            (uint)*(byte *)((longlong)param_1 + 0x21b) == 2) {
          if (*(char *)((longlong)param_1 + 0x21c) == '\x03') {
            lVar8 = FUN_14013a61c();
            if (lVar8 != 0) {
              FUN_1400018a8(&local_68,lVar8,0x191);
              puVar7 = local_68;
            }
            FUN_140003cf0(&local_70,puVar7,DAT_1401f6818,local_78);
            local_78 = local_78 + 1;
          }
          else {
            uVar10 = FUN_14008cd18(param_1,&local_58,*(char *)((longlong)param_1 + 0x21c));
            FID_conflict_operator_(&local_70,uVar10);
            LOCK();
            piVar1 = (int *)(local_58 + -8);
            iVar5 = *piVar1;
            *piVar1 = *piVar1 + -1;
            UNLOCK();
            puVar7 = local_68;
            if (iVar5 + -1 < 1) {
              (**(code **)(**(longlong **)(local_58 + -0x18) + 8))();
              puVar7 = local_68;
            }
          }
          if (param_2 != '\0') {
            FUN_140143988(DAT_140630c70);
          }
          if (DAT_1405a7338 != 0) {
            FUN_1400a1378(1);
            FUN_1400a1318();
            FUN_140156b40(&DAT_1405a5df0);
            FUN_1400a1378(1);
          }
          cVar3 = *(char *)((longlong)param_1 + 0x21c);
          if (((cVar3 == '\x02') || (cVar3 == '\x04')) || (cVar3 == '\x11')) {
            LOCK();
            piVar1 = (int *)(puVar7 + -1);
            iVar5 = *piVar1;
            *piVar1 = *piVar1 + -1;
            UNLOCK();
            if (iVar5 + -1 < 1) {
              (**(code **)(*(longlong *)puVar7[-3] + 8))();
            }
            LOCK();
            piVar1 = (int *)(local_70 + -8);
            iVar5 = *piVar1;
            *piVar1 = *piVar1 + -1;
            UNLOCK();
            if (iVar5 + -1 < 1) {
              (**(code **)(**(longlong **)(local_70 + -0x18) + 8))();
            }
            return 0xffffffff;
          }
          if ((cVar3 == '\n') || (cVar3 == '\f')) {
            LOCK();
            piVar1 = (int *)(puVar7 + -1);
            iVar5 = *piVar1;
            *piVar1 = *piVar1 + -1;
            UNLOCK();
            if (iVar5 + -1 < 1) {
              (**(code **)(*(longlong *)puVar7[-3] + 8))();
            }
            LOCK();
            piVar1 = (int *)(local_70 + -8);
            iVar5 = *piVar1;
            *piVar1 = *piVar1 + -1;
            UNLOCK();
            if (iVar5 + -1 < 1) {
              (**(code **)(**(longlong **)(local_70 + -0x18) + 8))();
            }
            return 0xffffffff;
          }
        }
      }
      else if (((param_2 != '\0') && (-1 < iVar5)) &&
              (lVar8 = FUN_14011b248(&DAT_140701ec0), 1000 < (longlong)(lVar8 - local_60))) {
        local_60 = FUN_14011b248(&DAT_140701ec0);
        FUN_140140b14(DAT_140630c70,&local_70);
        if (*(int *)(local_70 + -0x10) < 2) {
          sVar9 = strlen("    ");
          FUN_140001a34(&local_70,&DAT_14019ef74,sVar9 & 0xffffffff);
        }
        FUN_14004d348(&local_70,*(int *)(local_70 + -0x10) + -1,local_40[lVar13]);
        lVar13 = lVar13 + 1;
        FUN_140143988(DAT_140630c70);
        if (3 < lVar13) {
          lVar13 = 0;
        }
      }
      iVar5 = 30000;
      if (param_3 == 2) {
        iVar5 = 7000;
      }
      lVar8 = FUN_14011b248(&DAT_140701ec0);
      if ((longlong)iVar5 < (longlong)(lVar8 - local_50)) break;
      sVar2 = *(short *)((longlong)param_1 + 0x214);
    }
    *(undefined1 *)((longlong)param_1 + 0x5874) = 1;
    (**(code **)(*param_1 + 0x38))(param_1);
LAB_140086a40:
    if (*(short *)((longlong)param_1 + 0x214) == 2) {
      if (param_2 != '\0') {
        FUN_140140b14(DAT_140630c70,&local_70);
        if (*(int *)(local_70 + -0x10) == 0) {
          sVar9 = strlen(".");
          FUN_140001a34(&local_70,&DAT_14019c0fc,sVar9 & 0xffffffff);
        }
        else {
          FUN_14004d348(&local_70,*(int *)(local_70 + -0x10) + -1,0x2e);
        }
        FUN_140143988(DAT_140630c70,local_70);
      }
      sVar9 = strlen("");
      FUN_140001a34(&local_70,&DAT_14019aa80,sVar9 & 0xffffffff);
      if (param_3 == 1) {
        DAT_14062fb4c = *(undefined1 *)((longlong)param_1 + 0x21c);
        DAT_14062fb4d = *(undefined1 *)((longlong)param_1 + 0x21d);
        DAT_1405a554c = 1;
        DAT_1405a71ec =
             (uint)*(byte *)((longlong)param_1 + 0x21e) * 0x100 +
             (uint)*(byte *)((longlong)param_1 + 0x21f);
        DAT_14070229c = DAT_1405a71ec;
        if ((*(byte *)((longlong)param_1 + 0x21a) & 1) == 0) {
          if ((*(byte *)((longlong)param_1 + 0x21a) & 2) == 0) {
            sVar9 = strlen("Kx");
            FUN_140001a34(&local_70,&DAT_1401ad75c,sVar9 & 0xffffffff);
            _DAT_1401f4988 = 0xffffffff;
          }
          else {
            sVar9 = strlen("K2");
            FUN_140001a34(&local_70,&DAT_1401ad758,sVar9 & 0xffffffff);
            _DAT_1401f4988 = 2;
          }
        }
        else {
          sVar9 = strlen("K1");
          FUN_140001a34(&local_70,&DAT_1401ad754,sVar9 & 0xffffffff);
          _DAT_1401f4988 = 1;
        }
        FUN_140003cf0(&local_68,"%s %dbps KW:%02X%02X",local_70,DAT_1405a71ec,DAT_14062fb4c,
                      DAT_14062fb4d);
      }
      else if (param_3 == 4) {
        DAT_14062fb4c = 0xef;
        DAT_14062fb4d = 0x8f;
        sVar9 = strlen("DoIP");
        FUN_140001a34(&local_68,&DAT_1401ad778,sVar9 & 0xffffffff);
        DAT_1405a554c = 4;
        DAT_1401f6814 = 2;
        DAT_14025bf84 = 3;
      }
      else {
        DAT_14062fb4c = 0xef;
        DAT_14062fb4d = 0x8f;
        DAT_1405a554c = 2;
        sVar9 = strlen("CAN");
        FUN_140001a34(&local_68,&DAT_14019c64c,sVar9 & 0xffffffff);
        if ((*(byte *)((longlong)param_1 + 0x21b) & 4) != 0) {
          DAT_14062fb4c = *(undefined1 *)((longlong)param_1 + 0x21c);
          DAT_14062fb4d = *(undefined1 *)((longlong)param_1 + 0x21d);
          DAT_1401f6814 = 0;
          sVar9 = strlen("-TP1.6");
          FUN_14000d230(&local_68,"-TP1.6",sVar9 & 0xffffffff);
        }
        if ((*(byte *)((longlong)param_1 + 0x21b) & 8) != 0) {
          if ((((DAT_1401f6818 == 0x87) || ((0x90 < DAT_1401f6818 && (DAT_1401f6818 < 0xa4)))) ||
              ((0xaf < DAT_1401f6818 && (DAT_1401f6818 < 0xb7)))) ||
             (((DAT_1401f6818 == 0xc1 || (DAT_1401f6818 == 0xd2)) || (DAT_1401f6818 == 0xd3)))) {
            DAT_1401f6814 = 3;
          }
          else {
            DAT_1401f6814 = 1;
          }
        }
        if ((*(byte *)((longlong)param_1 + 0x21b) & 0x10) != 0) {
          DAT_1401f6814 = 2;
          sVar9 = strlen("UDS");
          FUN_140001a34(&local_68,&DAT_14019c640,sVar9 & 0xffffffff);
          if (param_4 == 0x30) {
            sVar9 = strlen("-R");
            FUN_14000d230(&local_68,&DAT_1401ad788,sVar9 & 0xffffffff);
            DAT_140638b38 = 1;
          }
          else if ((*(byte *)((longlong)param_1 + 0x21b) & 0x20) != 0) {
            sVar9 = strlen("-R");
            FUN_14000d230(&local_68,&DAT_1401ad788,sVar9 & 0xffffffff);
            DAT_140638b38 = 1;
          }
          if (((DAT_1401f6818 == 0x87) || ((0x90 < DAT_1401f6818 && (DAT_1401f6818 < 0xa4)))) ||
             (((0xaf < DAT_1401f6818 && (DAT_1401f6818 < 0xb7)) ||
              (((DAT_1401f6818 == 0xc1 || (DAT_1401f6818 == 0xd2)) || (DAT_1401f6818 == 0xd3)))))) {
            uVar14 = 0x92;
          }
          DAT_14025bf84 = uVar14;
          if (DAT_1401f6818 == 0x97) {
            DAT_14025bf84 = 1;
          }
        }
        if ((*(byte *)((longlong)param_1 + 0x21b) & 0x20) != 0) {
          DAT_1401f6814 = 2;
          DAT_1405a55d8 = 4;
          *(undefined4 *)(param_1 + 1) = 0;
          iVar5 = 0;
          pbVar11 = (byte *)((longlong)param_1 + 0x21f);
          do {
            *(uint *)(param_1 + 1) = (int)param_1[1] + ((uint)*pbVar11 << ((byte)iVar5 & 0x1f));
            iVar5 = iVar5 + 8;
            pbVar11 = pbVar11 + -1;
          } while (iVar5 < 0x20);
        }
      }
      puVar7 = local_68;
      if (param_2 != '\0') {
        FUN_140143988(DAT_140630c80,local_68);
      }
      if (DAT_1405a7338 != 0) {
        FUN_1400a1378(1);
        FUN_1400a1318();
        FUN_140156b40(&DAT_1405a5df0,puVar7);
        FUN_1400a1378(1);
      }
      if (param_3 == 2) {
        if ((*(byte *)((longlong)param_1 + 0x21b) & 4) == 0) {
          FUN_1400a143c(100);
        }
        else {
          FUN_1400a143c(200);
        }
      }
      DAT_1401f6e4c = 1000;
      DAT_140631ec4 = 1;
      uVar14 = FUN_14011b248(&DAT_140701ec0);
      *(undefined4 *)(param_1 + 0x34) = uVar14;
      LOCK();
      piVar1 = (int *)(puVar7 + -1);
      iVar5 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (iVar5 == 1 || iVar5 + -1 < 0) {
        (**(code **)(*(longlong *)puVar7[-3] + 8))();
      }
      LOCK();
      piVar1 = (int *)(local_70 + -8);
      iVar5 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (iVar5 + -1 < 1) {
        (**(code **)(**(longlong **)(local_70 + -0x18) + 8))();
      }
      uVar10 = 0;
    }
    else {
      LOCK();
      piVar1 = (int *)(puVar7 + -1);
      iVar5 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (iVar5 + -1 < 1) {
        (**(code **)(*(longlong *)puVar7[-3] + 8))();
      }
      LOCK();
      piVar1 = (int *)(local_70 + -8);
      iVar5 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (iVar5 == 1 || iVar5 + -1 < 0) {
        (**(code **)(**(longlong **)(local_70 + -0x18) + 8))();
      }
      uVar10 = 0xffffffff;
    }
  }
  else {
    DAT_14025bf68 = 1;
    LOCK();
    piVar1 = (int *)(puVar7 + -1);
    iVar5 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar5 + -1 < 1) {
      (**(code **)(*(longlong *)puVar7[-3] + 8))();
    }
    LOCK();
    piVar1 = (int *)(local_70 + -8);
    iVar5 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar5 + -1 < 1) {
      (**(code **)(**(longlong **)(local_70 + -0x18) + 8))();
    }
    uVar10 = 0xffffffff;
  }
  return uVar10;
}


```


### String `"\"Error s%d. Please contact Ross-tech\""` at `1401ad790`
- **XREF from:** `140087075` in Function **`FUN_140086fa4`** (`140086fa4`)

```c
// Function: FUN_140086fa4 @ 140086fa4

int FUN_140086fa4(longlong param_1,uint param_2,void *param_3,ulonglong param_4)

{
  int *piVar1;
  void *_Memory;
  char *pcVar2;
  ushort uVar3;
  int iVar4;
  longlong *plVar5;
  longlong lVar6;
  uint uVar7;
  byte bVar8;
  int iVar9;
  ushort uVar10;
  char *local_res8;
  undefined8 uVar11;
  undefined1 local_8c8;
  undefined1 local_8c7;
  undefined1 local_8c6;
  undefined1 local_8c5;
  undefined1 local_8c4;
  byte local_8c3;
  byte local_8c2;
  undefined1 local_8c1 [2201];
  
  uVar11 = 0xfffffffffffffffe;
  uVar10 = (ushort)param_4;
  iVar9 = 0;
  if ((short)param_2 != 8) {
    *(undefined4 *)(param_1 + 0x5cb8) = 0;
    *(undefined4 *)(param_1 + 0x1a4) = 0xffffffff;
    *(undefined4 *)(param_1 + 0x1a8) = 0;
    *(uint *)(param_1 + 0x5ccc) = param_2 & 0xffff;
  }
  local_8c8 = *(undefined1 *)(param_1 + 0x1b8);
  local_8c7 = 0;
  local_8c6 = (undefined1)(param_2 >> 8);
  local_8c5 = (undefined1)param_2;
  local_8c4 = (undefined1)(param_4 >> 8);
  bVar8 = (byte)param_4;
  local_8c3 = bVar8;
  if (0x406 < uVar10) {
    plVar5 = (longlong *)FUN_14013a630();
    if (plVar5 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
      FUN_140001000(0x80004005);
    }
    lVar6 = (**(code **)(*plVar5 + 0x18))();
    local_res8 = (char *)(lVar6 + 0x18);
    FUN_140003cf0(&local_res8);
    pcVar2 = local_res8;
    AfxMessageBox(local_res8,0,0);
    LOCK();
    piVar1 = (int *)(pcVar2 + -8);
    iVar4 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar4 + -1 < 1) {
      (**(code **)(**(longlong **)(pcVar2 + -0x18) + 8))();
    }
  }
  if (0xdb < uVar10) {
    uVar7 = 0xdb;
    if (uVar10 == 0) {
      return -1;
    }
    do {
      local_8c2 = (byte)uVar7;
      memcpy(local_8c1,(void *)((longlong)iVar9 + (longlong)param_3),(longlong)(int)uVar7);
      iVar4 = FUN_140088ed8(param_1,&local_8c8,uVar7 + 7,0,uVar11);
      FUN_140089688(param_1);
      local_8c8 = *(undefined1 *)(param_1 + 0x1b8);
      iVar9 = iVar9 + uVar7;
      uVar3 = uVar10 - (short)iVar9;
      uVar7 = 0xdb;
      if (uVar3 < 0xdc) {
        uVar7 = (uint)uVar3;
      }
    } while (iVar9 < (int)(uint)uVar10);
    return iVar4;
  }
  local_8c2 = bVar8;
  if (bVar8 != 0) {
    memcpy(local_8c1,param_3,param_4 & 0xff);
  }
  iVar9 = FUN_140088ed8(param_1,&local_8c8,bVar8 + 7,0,uVar11);
  if (-1 < iVar9) goto LAB_1400871f0;
  DAT_140631e44 = 1;
  if (DAT_1405a55e4 == 8) {
    _Memory = *(void **)(param_1 + 0x208);
    if (_Memory != (void *)0x0) {
      FUN_140132980(_Memory);
      free(_Memory);
      *(undefined8 *)(param_1 + 0x208) = 0;
      goto LAB_1400871b6;
    }
  }
  else {
LAB_1400871b6:
    if (DAT_1405a55e4 == 7) {
      if (0 < *(int *)(param_1 + 0x1cc)) {
        (*DAT_14018cf60)((longlong)*(int *)(param_1 + 0x1cc));
      }
      if (0 < *(int *)(param_1 + 0x1d0)) {
        (*DAT_14018cf60)((longlong)*(int *)(param_1 + 0x1d0));
      }
      (*DAT_14018cf00)();
    }
  }
  *(undefined1 *)(param_1 + 0x5ce0) = 0;
LAB_1400871f0:
  FUN_140089688(param_1);
  return iVar9;
}


```


### String `"\"USB init failed\n\""` at `1401adb10`
- **XREF from:** `14008a486` in Function **`FUN_14008a404`** (`14008a404`)

```c
// Function: FUN_14008a404 @ 14008a404

undefined8 FUN_14008a404(longlong param_1,char param_2)

{
  uint uVar1;
  int iVar2;
  void *pvVar3;
  undefined8 uVar4;
  size_t sVar5;
  undefined1 local_res8 [8];
  
  if (*(longlong *)(param_1 + 0x208) == 0) {
    pvVar3 = operator_new(0x8168);
    if (pvVar3 == (void *)0x0) {
      uVar4 = 0;
    }
    else {
      uVar4 = FUN_1401328f4(pvVar3);
    }
    *(undefined8 *)(param_1 + 0x208) = uVar4;
  }
  iVar2 = FUN_140132bfc(*(undefined8 *)(param_1 + 0x208),0x483,0xa00f);
  if ((iVar2 == 0) &&
     (iVar2 = FUN_140132bfc(*(undefined8 *)(param_1 + 0x208),0x483,0xa0cb), iVar2 == 0)) {
    uVar4 = FUN_140001790(local_res8,"USB init failed\n");
    FUN_140098d5c(param_1,uVar4);
    return 0xffffffff;
  }
  if (DAT_140630c7c == -0x5f35) {
    sVar5 = strlen("HEX-V2");
    FUN_140001a34(param_1 + 0x18,"HEX-V2",sVar5 & 0xffffffff);
    if (param_2 == '\0') {
      FUN_14008ffb0(param_1,2);
      goto LAB_14008a4e4;
    }
  }
  else {
LAB_14008a4e4:
    if (param_2 == '\0') goto LAB_14008a553;
  }
  iVar2 = FUN_14008ffb0(param_1,0);
  if ((iVar2 == 0) && (DAT_140630c7c == -0x5ff1)) {
    FUN_14008ffb0(param_1,1);
    uVar1 = *(uint *)(param_1 + 0x158);
    if ((uVar1 != 0) && (uVar1 != 0x100a8c0)) {
      FUN_140003cf0(param_1 + 0x30,"\n    IP: %d.%d.%d.%d",uVar1 & 0xff,
                    *(undefined1 *)(param_1 + 0x159),*(undefined1 *)(param_1 + 0x15a),
                    *(undefined1 *)(param_1 + 0x15b));
    }
  }
LAB_14008a553:
  if (*(char *)(param_1 + 0x5ce0) == '\0') {
    FUN_140086fa4(param_1,0x24,0);
    FUN_14008734c(param_1,0);
    if ((*(short *)(param_1 + 0x214) == 7) && (*(char *)(param_1 + 0x21a) == '\0')) {
      uVar4 = FUN_140001790(local_res8,"Interface busy");
      FUN_140098d5c(param_1,uVar4);
      return 0xfffffffd;
    }
  }
  return 0;
}


```


### String `"\"vusb_off_time\""` at `1401adcc0`
- **XREF from:** `14008d1ce` in Function **`FUN_14008d034`** (`14008d034`)

```c
// Function: FUN_14008d034 @ 14008d034

/* WARNING: Globals starting with '_' overlap smaller symbols at the same address */

undefined8 FUN_14008d034(longlong param_1)

{
  int *piVar1;
  undefined4 uVar2;
  longlong *plVar3;
  undefined8 *puVar4;
  size_t sVar5;
  undefined8 uVar6;
  longlong lVar7;
  uint uVar8;
  int iVar9;
  uint *puVar10;
  undefined8 *local_res10;
  uint local_40 [10];
  
  plVar3 = (longlong *)FUN_14013a630();
  if (plVar3 != (longlong *)0x0) {
    puVar4 = (undefined8 *)(**(code **)(*plVar3 + 0x18))(plVar3);
    local_res10 = puVar4 + 3;
    FUN_140086fa4(param_1,0x4a,0);
    FUN_14008734c(param_1,0x4a);
    if (*(short *)(param_1 + 0x214) == 0x4a) {
      if (DAT_1405a7338 != 0) {
        FUN_1400a1378();
        FUN_1400a1318();
      }
      iVar9 = 0;
      puVar10 = local_40;
      param_1 = param_1 + 0x21a;
      do {
        *puVar10 = 0;
        lVar7 = 0;
        uVar8 = 0;
        do {
          uVar8 = uVar8 << 8 | (uint)*(byte *)(param_1 + lVar7);
          lVar7 = lVar7 + 1;
        } while (lVar7 < 4);
        *puVar10 = uVar8;
        if (iVar9 == 0) {
          sVar5 = strlen("cur_time");
          FUN_140001a34(&local_res10,"cur_time",sVar5 & 0xffffffff);
        }
        else if (iVar9 == 1) {
          sVar5 = strlen("ign_off_time");
          FUN_140001a34(&local_res10,"ign_off_time",sVar5 & 0xffffffff);
        }
        else if (iVar9 == 2) {
          sVar5 = strlen("ign_on_time");
          FUN_140001a34(&local_res10,"ign_on_time",sVar5 & 0xffffffff);
        }
        else if (iVar9 == 3) {
          sVar5 = strlen("vbatt_off_time");
          FUN_140001a34(&local_res10,"vbatt_off_time",sVar5 & 0xffffffff);
        }
        else if (iVar9 == 4) {
          sVar5 = strlen("vbatt_on_time");
          FUN_140001a34(&local_res10,"vbatt_on_time",sVar5 & 0xffffffff);
        }
        else if (iVar9 == 5) {
          sVar5 = strlen("vusb_off_time");
          FUN_140001a34(&local_res10,"vusb_off_time",sVar5 & 0xffffffff);
        }
        else if (iVar9 == 6) {
          sVar5 = strlen("vusb_on_time");
          FUN_140001a34(&local_res10,"vusb_on_time",sVar5 & 0xffffffff);
        }
        else if (iVar9 == 7) {
          sVar5 = strlen("vin_check_time");
          FUN_140001a34(&local_res10,"vin_check_time",sVar5 & 0xffffffff);
        }
        else {
          sVar5 = strlen("???");
          FUN_140001a34(&local_res10,&DAT_1401adcf0,sVar5 & 0xffffffff);
        }
        puVar4 = local_res10;
        sprintf(&DAT_1405a59f0,"%s: %1.3fs; ",local_res10,(double)(int)uVar8 * _DAT_1401c5d08);
        FUN_140156b40(&DAT_1405a5df0,&DAT_1405a59f0);
        iVar9 = iVar9 + 1;
        param_1 = param_1 + 4;
        puVar10 = puVar10 + 1;
      } while (iVar9 < 8);
      uVar2 = DAT_140631f20;
      if (DAT_1405a7338 == 0) {
        sVar5 = strlen(&DAT_1405a5df0);
        FUN_140001a34(&local_res10,&DAT_1405a5df0,sVar5 & 0xffffffff);
        uVar2 = DAT_140631f20;
        DAT_140631f20 = 9999;
        DAT_1405a55d0 = (FILE *)FUN_140156994(".\\Debug\\DEBUG-VIN.DLM",&DAT_14019af8c);
        FUN_1400a1318();
        puVar4 = local_res10;
        FUN_140156b40(&DAT_1405a5df0,local_res10);
        FUN_1400a1378(1);
        sprintf(&DAT_1405a59f0,"VBatt: %dmV; VIgn: %dmV; GVL Xsum:%08X",(ulonglong)DAT_14021b768,
                (ulonglong)DAT_14025bf64,DAT_14025c4b8);
        FUN_1400a1318();
        FUN_140156b40(&DAT_1405a5df0,&DAT_1405a59f0);
        FUN_1400a1378(1);
        if (DAT_1405a55d0 != (FILE *)0x0) {
          fclose(DAT_1405a55d0);
        }
      }
      DAT_140631f20 = uVar2;
      LOCK();
      piVar1 = (int *)(puVar4 + -1);
      iVar9 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (iVar9 + -1 < 1) {
        (**(code **)(*(longlong *)puVar4[-3] + 8))();
      }
      uVar6 = 1;
    }
    else {
      LOCK();
      piVar1 = (int *)(puVar4 + 2);
      iVar9 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (iVar9 + -1 < 1) {
        (**(code **)(*(longlong *)*puVar4 + 8))();
      }
      uVar6 = 0xffffffff;
    }
    return uVar6;
  }
                    /* WARNING: Subroutine does not return */
  FUN_140001000(0x80004005);
}


```

- **XREF from:** `14008d1dd` in Function **`FUN_14008d034`** (`14008d034`)

### String `"\"vusb_on_time\""` at `1401adcd0`
- **XREF from:** `14008d1a6` in Function **`FUN_14008d034`** (`14008d034`)
- **XREF from:** `14008d1b5` in Function **`FUN_14008d034`** (`14008d034`)

### String `"\"-TP2.0\""` at `1401add58`
- **XREF from:** `14008d811` in Function **`FUN_14008d414`** (`14008d414`)

```c
// Function: FUN_14008d414 @ 14008d414

undefined8 FUN_14008d414(longlong *param_1)

{
  int *piVar1;
  char cVar2;
  undefined4 uVar3;
  short sVar4;
  uint uVar5;
  int iVar6;
  int iVar7;
  longlong *plVar8;
  ulonglong uVar9;
  undefined8 uVar10;
  longlong lVar11;
  size_t sVar12;
  longlong lVar13;
  int iVar14;
  byte *pbVar15;
  longlong local_res18;
  longlong local_res20;
  longlong local_78;
  longlong local_70;
  undefined8 local_68;
  undefined1 local_60 [16];
  undefined8 local_50;
  undefined8 local_48;
  undefined1 local_40;
  undefined1 local_3f;
  
  local_68 = 0xfffffffffffffffe;
  plVar8 = (longlong *)FUN_14013a630();
  lVar13 = 0;
  if (plVar8 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  local_res18 = (**(code **)(*plVar8 + 0x18))(plVar8);
  local_res18 = local_res18 + 0x18;
  plVar8 = (longlong *)FUN_14013a630();
  if (plVar8 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  local_res20 = (**(code **)(*plVar8 + 0x18))(plVar8);
  local_res20 = local_res20 + 0x18;
  uVar5 = FUN_14011b248(&DAT_140701ec0);
  *(uint *)((longlong)param_1 + 0x1bc) = uVar5;
  uVar9 = (ulonglong)uVar5;
  FUN_140156be0(local_60,&DAT_1401ad74c);
  FUN_140086fa4(param_1,0x49,0);
  FUN_14008734c(param_1,1);
  local_78 = 0;
  sVar4 = *(short *)((longlong)param_1 + 0x214);
  do {
    uVar3 = DAT_140631f20;
    if ((sVar4 == 0x49) || (*(short *)((longlong)param_1 + 0x214) == 4)) goto LAB_14008dcce;
    iVar6 = (**(code **)(*param_1 + 0xf8))(param_1,0);
    iVar7 = FUN_14008734c(param_1);
    uVar3 = DAT_140631f20;
    if (DAT_1405a7338 == 0) {
      DAT_140631f20 = 9999;
      DAT_1405a55d0 = (FILE *)FUN_140156994(".\\Debug\\DEBUG-VIN.DLM",&DAT_14019af8c);
      FUN_1400a1378(1);
      FUN_1400a1318();
      iVar14 = 0;
      if (iVar7 < 0) {
        FUN_140156b40(&DAT_1405a5df0);
      }
      else {
        sprintf(&DAT_1405a59f0,"Cmd: %d, RespInfo:",
                (ulonglong)*(ushort *)((longlong)param_1 + 0x214));
        FUN_140156b40(&DAT_1405a5df0);
        lVar13 = local_78;
        if ((short)param_1[0x43] != 0) {
          pbVar15 = (byte *)((longlong)param_1 + 0x21a);
          do {
            sprintf(&DAT_1405a59f0," %02X",(ulonglong)*pbVar15);
            FUN_140156b40(&DAT_1405a5df0);
            iVar14 = iVar14 + 1;
            pbVar15 = pbVar15 + 1;
          } while (iVar14 < (int)(uint)*(ushort *)(param_1 + 0x43));
        }
      }
      FUN_1400a1378(1);
      if (DAT_1405a55d0 != (FILE *)0x0) {
        fclose(DAT_1405a55d0);
      }
    }
    DAT_140631f20 = uVar3;
    (*DAT_14018c720)(1);
    if (iVar7 < 0) {
LAB_14008d979:
      sVar4 = *(short *)((longlong)param_1 + 0x214);
      if (sVar4 != 2) goto LAB_14008d985;
LAB_14008d98f:
      if ((-1 < iVar6) &&
         (lVar11 = FUN_14011b248(&DAT_140701ec0), 2000 < (longlong)(lVar11 - uVar9))) {
        uVar9 = FUN_14011b248(&DAT_140701ec0);
        FUN_140140b14(DAT_140630c70,&local_res18);
        if (*(int *)(local_res18 + -0x10) < 2) {
          sVar12 = strlen("    ");
          FUN_140001a34(&local_res18,&DAT_14019ef74,sVar12 & 0xffffffff);
        }
        FUN_14004d348(&local_res18,*(int *)(local_res18 + -0x10) + -1,local_60[lVar13]);
        lVar13 = lVar13 + 1;
        FUN_140143988(DAT_140630c70);
        local_78 = lVar13;
        if (3 < lVar13) {
          lVar13 = 0;
          local_78 = 0;
        }
      }
    }
    else {
      sVar4 = *(short *)((longlong)param_1 + 0x214);
      if ((sVar4 == 7) || (sVar4 == 2)) {
        uVar9 = FUN_14011b248(&DAT_140701ec0);
        if (*(short *)((longlong)param_1 + 0x214) == 7) {
          if ((uint)*(byte *)((longlong)param_1 + 0x21a) * 0x100 +
              (uint)*(byte *)((longlong)param_1 + 0x21b) == 2) {
            uVar10 = FUN_14008cd18(param_1,&local_70,*(undefined1 *)((longlong)param_1 + 0x21c));
            FID_conflict_operator_(&local_res18,uVar10);
            LOCK();
            piVar1 = (int *)(local_70 + -8);
            iVar7 = *piVar1;
            *piVar1 = *piVar1 + -1;
            UNLOCK();
            if (iVar7 + -1 < 1) {
              (**(code **)(**(longlong **)(local_70 + -0x18) + 8))();
            }
          }
        }
        else {
          if ((*(byte *)((longlong)param_1 + 0x21a) & 0x20) != 0) {
            sVar12 = strlen("DoIP");
            FUN_140001a34(&local_res18,&DAT_1401ad778,sVar12 & 0xffffffff);
          }
          if ((*(byte *)((longlong)param_1 + 0x21a) & 4) != 0) {
            sVar12 = strlen("CAN");
            FUN_140001a34(&local_res18,&DAT_14019c64c,sVar12 & 0xffffffff);
          }
          if (((*(byte *)((longlong)param_1 + 0x21a) & 1) != 0) ||
             ((*(byte *)((longlong)param_1 + 0x21a) & 2) != 0)) {
            sVar12 = strlen("K");
            FUN_140001a34(&local_res18,&DAT_1401add44,sVar12 & 0xffffffff);
          }
          if ((*(byte *)((longlong)param_1 + 0x21b) & 1) != 0) {
            sVar12 = strlen("-KW1281");
            FUN_14000d230(&local_res18,"-KW1281",sVar12 & 0xffffffff);
          }
          if ((*(byte *)((longlong)param_1 + 0x21b) & 2) != 0) {
            sVar12 = strlen("-KW2000");
            FUN_14000d230(&local_res18,"-KW2000",sVar12 & 0xffffffff);
          }
          if ((*(byte *)((longlong)param_1 + 0x21b) & 4) != 0) {
            sVar12 = strlen("-TP1.6");
            FUN_14000d230(&local_res18,"-TP1.6",sVar12 & 0xffffffff);
          }
          if ((*(byte *)((longlong)param_1 + 0x21b) & 8) != 0) {
            sVar12 = strlen("-TP2.0");
            FUN_14000d230(&local_res18,"-TP2.0",sVar12 & 0xffffffff);
          }
          if ((*(byte *)((longlong)param_1 + 0x21b) & 0x10) != 0) {
            sVar12 = strlen("-UDS");
            FUN_14000d230(&local_res18,&DAT_1401add60,sVar12 & 0xffffffff);
          }
          if ((*(byte *)((longlong)param_1 + 0x21b) & 0x20) != 0) {
            sVar12 = strlen("-ISO15765");
            FUN_14000d230(&local_res18,"-ISO15765",sVar12 & 0xffffffff);
          }
        }
        FUN_140143988(DAT_140630c70,local_res18);
        uVar3 = DAT_140631f20;
        if (DAT_1405a7338 == 0) {
          DAT_140631f20 = 9999;
          DAT_1405a55d0 = (FILE *)FUN_140156994(".\\Debug\\DEBUG-VIN.DLM",&DAT_14019af8c);
          FUN_1400a1378(1);
          FUN_1400a1318();
          FUN_140156b40(&DAT_1405a5df0);
          FUN_1400a1378(1);
          if (DAT_1405a55d0 != (FILE *)0x0) {
            fclose(DAT_1405a55d0);
          }
        }
        else {
          FUN_1400a1378(1);
          FUN_1400a1318();
          FUN_140156b40(&DAT_1405a5df0);
          FUN_1400a1378(1);
          uVar3 = DAT_140631f20;
        }
        DAT_140631f20 = uVar3;
        cVar2 = *(char *)((longlong)param_1 + 0x21c);
        if (((cVar2 == '\x02') || (cVar2 == '\x04')) || (cVar2 == '\x11')) {
          LOCK();
          piVar1 = (int *)(local_res20 + -8);
          iVar6 = *piVar1;
          *piVar1 = *piVar1 + -1;
          UNLOCK();
          if (iVar6 + -1 < 1) {
            (**(code **)(**(longlong **)(local_res20 + -0x18) + 8))();
          }
          LOCK();
          piVar1 = (int *)(local_res18 + -8);
          iVar6 = *piVar1;
          *piVar1 = *piVar1 + -1;
          UNLOCK();
          if (iVar6 + -1 < 1) {
            (**(code **)(**(longlong **)(local_res18 + -0x18) + 8))();
          }
          return 0xffffffff;
        }
        if ((cVar2 == '\n') || (cVar2 == '\f')) {
          LOCK();
          piVar1 = (int *)(local_res20 + -8);
          iVar6 = *piVar1;
          *piVar1 = *piVar1 + -1;
          UNLOCK();
          if (iVar6 + -1 < 1) {
            (**(code **)(**(longlong **)(local_res20 + -0x18) + 8))();
          }
          LOCK();
          piVar1 = (int *)(local_res18 + -8);
          iVar6 = *piVar1;
          *piVar1 = *piVar1 + -1;
          UNLOCK();
          if (iVar6 + -1 < 1) {
            (**(code **)(**(longlong **)(local_res18 + -0x18) + 8))();
          }
          return 0xffffffff;
        }
        goto LAB_14008d979;
      }
LAB_14008d985:
      if (sVar4 == 6) goto LAB_14008d98f;
    }
    if (*(short *)((longlong)param_1 + 0x214) == 3) {
      iVar6 = FUN_14008734c(param_1,0);
      uVar3 = DAT_140631f20;
      if (DAT_1405a7338 == 0) {
        DAT_140631f20 = 9999;
        DAT_1405a55d0 = (FILE *)FUN_140156994(".\\Debug\\DEBUG-VIN.DLM",&DAT_14019af8c);
        FUN_1400a1378(1);
        FUN_1400a1318();
        iVar7 = 0;
        if (iVar6 < 0) {
          FUN_140156b40(&DAT_1405a5df0,"No response");
        }
        else {
          sprintf(&DAT_1405a59f0,"Cmd: %d, RespInfo:",
                  (ulonglong)*(ushort *)((longlong)param_1 + 0x214));
          FUN_140156b40(&DAT_1405a5df0,&DAT_1405a59f0);
          if ((short)param_1[0x43] != 0) {
            pbVar15 = (byte *)((longlong)param_1 + 0x21a);
            do {
              sprintf(&DAT_1405a59f0," %02X",(ulonglong)*pbVar15);
              FUN_140156b40(&DAT_1405a5df0,&DAT_1405a59f0);
              iVar7 = iVar7 + 1;
              pbVar15 = pbVar15 + 1;
            } while (iVar7 < (int)(uint)*(ushort *)(param_1 + 0x43));
          }
        }
        FUN_1400a1378(1);
        if (DAT_1405a55d0 != (FILE *)0x0) {
          fclose(DAT_1405a55d0);
        }
      }
      goto LAB_14008dcce;
    }
    lVar11 = FUN_14011b248(&DAT_140701ec0);
    if (4000 < (longlong)(lVar11 - (ulonglong)uVar5)) {
      lVar13 = FUN_14013a61c(0x51b);
      if (lVar13 != 0) {
        FUN_1400018a8(&local_res18,lVar13,0x51b);
      }
      FUN_140143988(DAT_140630c70,local_res18);
      *(undefined1 *)((longlong)param_1 + 0x5874) = 1;
      (**(code **)(*param_1 + 0x38))(param_1);
      FUN_1400a143c(1000);
      uVar3 = DAT_140631f20;
LAB_14008dcce:
      DAT_140631f20 = uVar3;
      uVar3 = DAT_140631f20;
      lVar13 = 0;
      if ((*(short *)((longlong)param_1 + 0x214) == 0x49) && (0x10 < *(ushort *)(param_1 + 0x43))) {
        local_50 = *(undefined8 *)((longlong)param_1 + 0x21a);
        local_48 = *(undefined8 *)((longlong)param_1 + 0x222);
        local_40 = *(undefined1 *)((longlong)param_1 + 0x22a);
        local_3f = 0;
        lVar11 = lVar13;
        do {
          iVar6 = isalnum((int)*(char *)((longlong)&local_50 + lVar11));
          if (iVar6 == 0) {
            DAT_140702495 = 1;
            if (DAT_140702496 != '\0') {
              sVar12 = strlen("");
              FUN_140001a34(&DAT_140638220,&DAT_14019aa80,sVar12 & 0xffffffff);
            }
            uVar3 = DAT_140631f20;
            DAT_140702496 = 0;
            if (DAT_1405a7338 == 0) {
              DAT_140631f20 = 9999;
              DAT_1405a55d0 = (FILE *)FUN_140156994(".\\Debug\\DEBUG-VIN.DLM",&DAT_14019af8c);
              FUN_1400a1378(1);
              FUN_1400a1318();
              sprintf(&DAT_1405a59f0,"VIN %s invalid",&local_50);
              FUN_140156b40(&DAT_1405a5df0,&DAT_1405a59f0);
              FUN_1400a1378(1);
              if (DAT_1405a55d0 != (FILE *)0x0) {
                fclose(DAT_1405a55d0);
              }
            }
            LOCK();
            piVar1 = (int *)(local_res20 + -8);
            iVar6 = *piVar1;
            DAT_140631f20 = uVar3;
            *piVar1 = *piVar1 + -1;
            UNLOCK();
            if (iVar6 + -1 < 1) {
              (**(code **)(**(longlong **)(local_res20 + -0x18) + 8))();
            }
            LOCK();
            piVar1 = (int *)(local_res18 + -8);
            iVar6 = *piVar1;
            *piVar1 = *piVar1 + -1;
            UNLOCK();
            if (iVar6 + -1 < 1) {
              (**(code **)(**(longlong **)(local_res18 + -0x18) + 8))();
            }
            return 0;
          }
          lVar11 = lVar11 + 1;
        } while (lVar11 < 0x11);
        sVar12 = strlen("");
        FUN_140001a34(&DAT_140638220,&DAT_14019aa80,sVar12 & 0xffffffff);
        do {
          FUN_140021d0c(&DAT_140638220,*(undefined1 *)((longlong)&local_50 + lVar13));
          uVar3 = DAT_140631f20;
          lVar13 = lVar13 + 1;
        } while (lVar13 < 0x11);
        DAT_140702496 = '\x01';
        DAT_140702495 = 0;
        if (DAT_1405a7338 == 0) {
          DAT_140631f20 = 9999;
          DAT_1405a55d0 = (FILE *)FUN_140156994(".\\Debug\\DEBUG-VIN.DLM",&DAT_14019af8c);
          FUN_1400a1378(1);
          FUN_1400a1318();
          sprintf(&DAT_1405a59f0,"VIN %s ACCEPTED!",&local_50);
          FUN_140156b40(&DAT_1405a5df0,&DAT_1405a59f0);
          FUN_1400a1378(1);
          if (DAT_1405a55d0 != (FILE *)0x0) {
            fclose(DAT_1405a55d0);
          }
          if (DAT_140631ed0 == 0) {
            DAT_140702592 = 1;
          }
        }
        LOCK();
        piVar1 = (int *)(local_res20 + -8);
        iVar6 = *piVar1;
        DAT_140631f20 = uVar3;
        *piVar1 = *piVar1 + -1;
        UNLOCK();
        if (iVar6 + -1 < 1) {
          (**(code **)(**(longlong **)(local_res20 + -0x18) + 8))();
        }
        LOCK();
        piVar1 = (int *)(local_res18 + -8);
        iVar6 = *piVar1;
        *piVar1 = *piVar1 + -1;
        UNLOCK();
        if (iVar6 + -1 < 1) {
          (**(code **)(**(longlong **)(local_res18 + -0x18) + 8))();
        }
        uVar10 = 1;
      }
      else {
        if (DAT_1405a7338 == 0) {
          DAT_140631f20 = 9999;
          DAT_1405a55d0 = (FILE *)FUN_140156994(".\\Debug\\DEBUG-VIN.DLM",&DAT_14019af8c);
          FUN_1400a1378(1);
          FUN_1400a1318();
          FUN_140156be0(&DAT_1405a59f0,"No valid VIN received");
          FUN_140156b40(&DAT_1405a5df0,&DAT_1405a59f0);
          FUN_1400a1378(1);
          if (DAT_1405a55d0 != (FILE *)0x0) {
            fclose(DAT_1405a55d0);
          }
        }
        LOCK();
        piVar1 = (int *)(local_res20 + -8);
        iVar6 = *piVar1;
        DAT_140631f20 = uVar3;
        *piVar1 = *piVar1 + -1;
        UNLOCK();
        if (iVar6 + -1 < 1) {
          (**(code **)(**(longlong **)(local_res20 + -0x18) + 8))();
        }
        LOCK();
        piVar1 = (int *)(local_res18 + -8);
        iVar6 = *piVar1;
        *piVar1 = *piVar1 + -1;
        UNLOCK();
        if (iVar6 + -1 < 1) {
          (**(code **)(**(longlong **)(local_res18 + -0x18) + 8))();
        }
        uVar10 = 0xffffffff;
      }
      return uVar10;
    }
    sVar4 = *(short *)((longlong)param_1 + 0x214);
  } while( true );
}


```

- **XREF from:** `14008d820` in Function **`FUN_14008d414`** (`14008d414`)

### String `"\"This interface appears to have an issue.\nPlease contact Ross-Tech with error code:\n%06X\""` at `1401ade10`
- **XREF from:** `14009020d` in Function **`FUN_14008ffb0`** (`14008ffb0`)

```c
// Function: FUN_14008ffb0 @ 14008ffb0

undefined8 FUN_14008ffb0(longlong param_1,int param_2)

{
  int *piVar1;
  int iVar2;
  longlong *plVar3;
  undefined8 *puVar4;
  ulonglong uVar5;
  longlong lVar6;
  char *pcVar7;
  char *local_res20;
  undefined8 uVar8;
  char local_8a8;
  char local_8a7;
  byte local_8a6;
  byte local_8a5;
  byte local_8a4;
  byte local_8a3;
  byte local_8a2;
  byte local_8a1;
  byte local_8a0;
  byte local_89f;
  byte local_89e;
  byte local_89d;
  byte abStack_892 [7];
  byte local_88b;
  byte local_88a;
  
  uVar8 = 0xfffffffffffffffe;
  plVar3 = (longlong *)FUN_14013a630();
  if (plVar3 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  puVar4 = (undefined8 *)(**(code **)(*plVar3 + 0x18))(plVar3);
  pcVar7 = (char *)(puVar4 + 3);
  if (param_2 == 0) {
    *(undefined4 *)(param_1 + 0x5c98) = 0;
    *(undefined4 *)(param_1 + 0x5c9c) = 0;
    *(undefined4 *)(param_1 + 0x5ca0) = 0;
    *(undefined4 *)(param_1 + 0x5ca4) = 0;
    local_8a6 = 0x14;
  }
  else if (param_2 == 1) {
    *(undefined4 *)(param_1 + 0x5ca8) = 0;
    *(undefined4 *)(param_1 + 0x5cb0) = 0;
    local_8a6 = 0xe2;
  }
  else {
    *(undefined4 *)(param_1 + 0x5cac) = 0;
    local_8a6 = 0x15;
  }
  local_8a8 = 'S';
  local_8a7 = '\x04';
  local_8a5 = local_8a6 ^ 0x57;
  local_res20 = pcVar7;
  if (DAT_1405a55e4 == 7) {
    (*DAT_14018cf70)((longlong)**(int **)(param_1 + 0x1d8),&local_8a8,4,0);
  }
  else if (*(longlong *)(param_1 + 0x208) != 0) {
    FUN_140132a10(*(longlong *)(param_1 + 0x208),&local_8a8,4,0,uVar8);
  }
  FUN_140090804(param_1,&local_8a8);
  if (param_2 == 0) {
    if ((local_8a8 < '\x0e') || (local_8a7 != '\x14')) {
      LOCK();
      piVar1 = (int *)(puVar4 + 2);
      iVar2 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (iVar2 + -1 < 1) {
        (**(code **)(*(longlong *)*puVar4 + 8))();
      }
      return 0xffffffff;
    }
    *(uint *)(param_1 + 0x5c98) =
         ((uint)local_8a6 * 0x100 + (uint)local_8a4) * 0x100 + (uint)local_8a5;
    *(uint *)(param_1 + 0x5c9c) = (uint)local_8a3;
    *(uint *)(param_1 + 0x5ca0) =
         ((uint)local_8a2 * 0x100 + (uint)local_8a0) * 0x100 + (uint)local_8a1;
    *(uint *)(param_1 + 0x5ca4) =
         ((uint)local_89f * 0x100 + (uint)local_89d) * 0x100 + (uint)local_89e;
  }
  else if (param_2 == 1) {
    if ((local_8a8 < '!') || (local_8a7 != -0x1e)) {
      LOCK();
      piVar1 = (int *)(puVar4 + 2);
      iVar2 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (iVar2 + -1 < 1) {
        (**(code **)(*(longlong *)*puVar4 + 8))();
      }
      return 0xffffffff;
    }
    iVar2 = (char)local_8a3 * 0x1000000 + (uint)local_8a5 * 0x100 + (uint)local_8a4 * 0x10000 +
            (uint)local_8a6;
    *(int *)(param_1 + 0x5cb0) = iVar2;
    if (DAT_1405a55e4 == 8) {
      *(int *)(param_1 + 0x158) = iVar2;
    }
    *(uint *)(param_1 + 0x5ca8) = (uint)local_88b * 0x100 + (uint)local_88a;
    uVar5 = 0;
    lVar6 = 0;
    do {
      uVar5 = uVar5 << 8 | (ulonglong)abStack_892[lVar6];
      lVar6 = lVar6 + 1;
    } while (lVar6 < 6);
    if (uVar5 == 0x1ec0000001) {
      FUN_140003cf0(&local_res20);
      pcVar7 = local_res20;
      AfxMessageBox(local_res20,0,0);
    }
  }
  else if (param_2 == 2) {
    if ((local_8a8 < '\b') || (local_8a7 != '\x15')) {
      LOCK();
      piVar1 = (int *)(puVar4 + 2);
      iVar2 = *piVar1;
      *piVar1 = *piVar1 + -1;
      UNLOCK();
      if (iVar2 + -1 < 1) {
        (**(code **)(*(longlong *)*puVar4 + 8))();
      }
      return 0xffffffff;
    }
    *(uint *)(param_1 + 0x5cac) = (uint)local_8a4 * 0x100 + (uint)local_8a5;
  }
  LOCK();
  piVar1 = (int *)(pcVar7 + -8);
  iVar2 = *piVar1;
  *piVar1 = *piVar1 + -1;
  UNLOCK();
  if (iVar2 + -1 < 1) {
    (**(code **)(**(longlong **)(pcVar7 + -0x18) + 8))();
  }
  return 0;
}


```


### String `"\"uCan-h\""` at `1401adfe8`
- **XREF from:** `140090da7` in Function **`FUN_140090d64`** (`140090d64`)

```c
// Function: FUN_140090d64 @ 140090d64

undefined8 *
FUN_140090d64(undefined8 *param_1,undefined8 param_2,undefined8 param_3,undefined8 param_4)

{
  size_t sVar1;
  undefined8 uVar2;
  
  uVar2 = 0xfffffffffffffffe;
  FUN_140098af8();
  *param_1 = &PTR_LAB_1401ae100;
  *(undefined1 *)((longlong)param_1 + 0x4c) = 1;
  *(undefined1 *)((longlong)param_1 + 0x49) = 0;
  *(undefined1 *)((longlong)param_1 + 0x4b) = 1;
  *(undefined1 *)(param_1 + 9) = 1;
  *(undefined1 *)((longlong)param_1 + 0x39) = 0;
  *(undefined1 *)((longlong)param_1 + 0x4a) = 1;
  sVar1 = strlen("uCan-h");
  FUN_140001a34(param_1 + 3,"uCan-h",sVar1 & 0xffffffff,param_4,uVar2);
  DAT_1405a554c = 2;
  *(undefined4 *)(param_1 + 0x36) = 0;
  FUN_140158320((longlong)param_1 + 0x1b4,0,0x800);
  *(undefined1 *)((longlong)param_1 + 0x9b4) = 0;
  *(undefined1 *)((longlong)param_1 + 0x9c5) = 0;
  return param_1;
}


```

- **XREF from:** `140090db6` in Function **`FUN_140090d64`** (`140090d64`)

### String `"\"uCAN-h\""` at `1401adff0`
- **XREF from non-function address:** `140090eab`

### String `"\"Ross-Tech uCAN-h\""` at `1401ae010`
- **XREF from non-function address:** `140091303`
- **XREF from non-function address:** `140091312`

### String `"\"\n**** Address %02X-%02X GetResponse() failed in SendCANMsg()\""` at `1401ae070`
- **XREF from:** `1400923e4` in Function **`FUN_140092120`** (`140092120`)

### String `"\".\\VCDSScan.exe\""` at `1401ae778`
- *(No direct XREFs found)*

### String `"\"uCan-c\""` at `1401af0bc`
- **XREF from:** `140099843` in Function **`FUN_1400997fc`** (`1400997fc`)

```c
// Function: FUN_1400997fc @ 1400997fc

undefined8 *
FUN_1400997fc(undefined8 *param_1,undefined8 param_2,undefined8 param_3,undefined8 param_4)

{
  size_t sVar1;
  undefined8 uVar2;
  
  uVar2 = 0xfffffffffffffffe;
  FUN_140098af8();
  *param_1 = &PTR_LAB_1401af170;
  *(undefined1 *)((longlong)param_1 + 0x4c) = 1;
  *(undefined1 *)((longlong)param_1 + 0x49) = 0;
  *(undefined1 *)((longlong)param_1 + 0x4b) = 1;
  *(undefined1 *)(param_1 + 9) = 1;
  *(undefined1 *)((longlong)param_1 + 0x39) = 0;
  *(undefined1 *)((longlong)param_1 + 0x4a) = 1;
  *(undefined1 *)(param_1 + 7) = 0;
  sVar1 = strlen("uCan-c");
  FUN_140001a34(param_1 + 3,"uCan-c",sVar1 & 0xffffffff,param_4,uVar2);
  DAT_1405a554c = 2;
  *(undefined4 *)(param_1 + 0x36) = 0;
  return param_1;
}


```

- **XREF from:** `140099852` in Function **`FUN_1400997fc`** (`1400997fc`)

### String `"\"uCAN-c\""` at `1401af0c4`
- **XREF from non-function address:** `14009a236`

### String `"\"Ross-Tech uCAN-c\""` at `1401af0d0`
- **XREF from non-function address:** `14009a5a8`
- **XREF from non-function address:** `14009a5b7`

### String `"\"KC::ScrCAN msk=0\""` at `1401af100`
- **XREF from:** `14009b386` in Function **`FUN_14009b328`** (`14009b328`)

```c
// Function: FUN_14009b328 @ 14009b328

undefined8 FUN_14009b328(longlong *param_1)

{
  bool bVar1;
  byte bVar2;
  int iVar3;
  undefined8 uVar4;
  int iVar5;
  undefined1 local_res8 [8];
  undefined1 local_res10 [8];
  undefined1 local_res18 [8];
  undefined1 local_68;
  undefined1 local_67;
  undefined1 local_66 [38];
  undefined1 local_40;
  char local_3f;
  
  if ((int)param_1[0x36] == 0) {
    iVar5 = 0;
    do {
      local_68 = 0x21;
      local_67 = 0xc;
      iVar3 = 0;
      do {
        FUN_140113214(&DAT_140701ec0,local_66,0x20);
        bVar2 = FUN_14009d3bc(param_1,local_66);
        if (bVar2 != 0) goto LAB_14009b3a5;
        bVar1 = iVar3 < 5;
        iVar3 = iVar3 + 1;
      } while (bVar1);
      uVar4 = FUN_140001790(local_res8,"KC::ScrCAN msk=0");
      FUN_140098d5c(param_1,uVar4);
LAB_14009b3a5:
      (**(code **)(*param_1 + 0x108))(param_1,&local_68);
      iVar3 = (**(code **)(*param_1 + 0x110))(param_1,&local_40);
      if (iVar3 != 0) {
        uVar4 = FUN_140001790(local_res10,"KC::ScrCAN() -1");
        FUN_140098d5c(param_1,uVar4);
      }
      if (local_3f != -0x74) {
        uVar4 = FUN_140001790(local_res18,"KC::ScrCAN() -2");
        FUN_140098d5c(param_1,uVar4);
      }
      FUN_1400a143c(iVar5);
      if (iVar3 == 0) {
        *(uint *)(param_1 + 0x36) = (uint)bVar2;
        goto LAB_14009b45f;
      }
      bVar1 = iVar5 < 0x9c4;
      iVar5 = iVar5 + 500;
    } while (bVar1);
    uVar4 = FUN_140001790(local_res8,"KC::ScrCAN() -3");
    FUN_140098d5c(param_1,uVar4);
    uVar4 = 0xffffffff;
  }
  else {
LAB_14009b45f:
    uVar4 = 0;
  }
  return uVar4;
}


```


### String `"\"KC::ScrCAN() -1\""` at `1401af118`
- **XREF from:** `14009b3ce` in Function **`FUN_14009b328`** (`14009b328`)

### String `"\"KC::ScrCAN() -2\""` at `1401af128`
- **XREF from:** `14009b3f4` in Function **`FUN_14009b328`** (`14009b328`)

### String `"\"KC::ScrCAN() -3\""` at `1401af138`
- **XREF from:** `14009b431` in Function **`FUN_14009b328`** (`14009b328`)

### String `"\"Ross-Tech \""` at `1401af290`
- **XREF from non-function address:** `14009bfbd`
- **XREF from non-function address:** `14009bfcc`

### String `"\"GetCanMsg() returned %d\""` at `1401af810`
- **XREF from:** `14009f0b2` in Function **`FUN_14009ef70`** (`14009ef70`)

```c
// Function: FUN_14009ef70 @ 14009ef70

ulonglong FUN_14009ef70(void)

{
  char cVar1;
  int iVar2;
  uint uVar3;
  uint uVar4;
  int iVar5;
  int iVar6;
  ulonglong uVar7;
  size_t sVar8;
  longlong lVar9;
  byte bVar10;
  longlong lVar11;
  ulonglong uVar12;
  uint uVar13;
  uint uVar14;
  int iVar15;
  ulonglong uVar16;
  ulonglong uVar17;
  int iVar18;
  int iVar19;
  char cVar20;
  bool bVar21;
  uint local_res8;
  byte local_res10 [8];
  int local_res18;
  int local_res20;
  int local_458;
  byte local_448;
  byte local_447 [1031];
  
  iVar5 = 1;
  local_res18 = 1;
  iVar6 = 0;
  cVar20 = '\0';
  local_res8 = local_res8 & 0xffffff00;
  if ((DAT_1405a55d8 == 2) && (DAT_140631ec4 == 0)) {
    uVar7 = FUN_1400a0d20();
  }
  else {
    uVar7 = FUN_14011b248(&DAT_140701ec0);
    if (DAT_1405a554c != 2) {
      FUN_1400a1378(0);
      FUN_1400a1318();
    }
    uVar12 = (ulonglong)DAT_1405e7b3c;
    iVar2 = 0;
    iVar15 = 0;
    local_res20 = 0;
    iVar18 = 0;
    local_458 = 0;
    uVar4 = local_res8;
    do {
      if ((iVar5 == 0) && (cVar20 == '\0')) goto LAB_14009f68b;
      iVar2 = iVar2 + 1;
      if (0x5dc < iVar2) {
        return 0xffffff9c;
      }
      if ((int)uVar12 == 0) {
        DAT_1401f6e4c = 1000;
      }
      if (((DAT_1401f6818 == 0x31) && (DAT_1405a4bc1 == '\x14')) && (iVar5 != 0)) {
        DAT_1401f6e4c = 0x9c4;
      }
      if (DAT_1405a554c == 2) {
        if (DAT_140631ec4 == 0) {
          uVar3 = FUN_14005eb08();
          FUN_1400a1378(1);
          FUN_1400a1318();
          sVar8 = strlen(&DAT_1405a5df0);
          sprintf(&DAT_1405a5df0 + sVar8,"GetCanMsg() returned %d",(ulonglong)uVar3);
          FUN_1400a1378(1);
          if ((int)uVar3 < 1) {
            if ((int)DAT_1405e7b3c < 1) goto LAB_14009f68b;
LAB_14009f683:
            DAT_1405e7b3c = DAT_1405e7b3c - 1;
            goto LAB_14009f68b;
          }
        }
        else {
LAB_14009f0f0:
          if ((int)uVar12 < 1) {
            if ((DAT_1401f6818 == 0x33) && (DAT_1405a55d8 == 2)) {
              DAT_1401f6e4c = 0x4b0;
            }
            else if ((iVar6 != 3) ||
                    ((DAT_1401f6818 != 0x53 || (DAT_1401f6e4c = 1000, DAT_1405a4bc1 != ';'))))
            goto LAB_14009f1bf;
          }
          else if (cVar20 == '\0') {
            lVar9 = FUN_14011b248(&DAT_140701ec0);
            if (((longlong)(lVar9 - (ulonglong)*(uint *)(DAT_140631e78 + 0x34)) < 0xfb) ||
               (iVar6 < 1)) {
              if ((DAT_1401f6818 == 0x46) && ((DAT_1405a4bc1 == '\x1a' && (DAT_1405a4bc2 == -0x65)))
                 ) {
                DAT_1401f6e4c = 0x2ee;
                goto LAB_14009f1c9;
              }
              DAT_1401f6e4c = 0xfa;
            }
            else {
              DAT_1401f6e4c = 0x2ee;
            }
            if ((DAT_1401f6818 == 0x31) && (DAT_1405a4bc1 == '\x14')) {
              DAT_1401f6e4c = 0x9c4;
            }
          }
          else {
LAB_14009f1bf:
            DAT_1401f6e4c = 4000;
          }
LAB_14009f1c9:
          uVar4 = (**(code **)(*DAT_140631e78 + 0xf0))(DAT_140631e78,&local_448);
          uVar3 = DAT_1405e7b3c;
          if (DAT_140631e44 != 0) {
            return 0xffffffff;
          }
          if (uVar4 == 0) {
            uVar12 = (ulonglong)local_448;
            local_res10[0] = local_447[0];
            bVar10 = local_447[0] & 0xc0;
            if (bVar10 == 0x40) {
              if (local_447[0] != 0x48) break;
              bVar21 = DAT_1405a55d8 == 2;
LAB_14009f2b1:
              if (!bVar21) break;
            }
            else if (bVar10 != 0x80) {
              bVar21 = bVar10 == 0xc0;
              goto LAB_14009f2b1;
            }
            lVar9 = 0;
            uVar13 = (uint)local_448;
            if (local_447[0] == 0x48) {
              uVar16 = 3;
              do {
                if ((longlong)uVar12 < lVar9 + 1) {
                  return 0xffffffff;
                }
                local_res10[lVar9 + 1] = local_447[lVar9 + 1];
                lVar11 = lVar9 + 2;
                lVar9 = lVar9 + 1;
              } while (lVar11 < 3);
              uVar14 = (uint)local_448;
              lVar9 = (longlong)(int)DAT_1405e7b3c * 0x480;
              (&DAT_1405e7b40)[lVar9] = local_448 + 0x7d;
              (&DAT_1405e7b41)[lVar9] = local_res10[1];
              (&DAT_1405e7b42)[lVar9] = local_res10[2];
            }
            else if ((local_447[0] & 0x7f) == 0) {
              uVar16 = 4;
              do {
                if ((longlong)uVar12 < lVar9 + 1) {
                  return 0xffffffff;
                }
                local_res10[lVar9 + 1] = local_447[lVar9 + 1];
                lVar11 = lVar9 + 2;
                lVar9 = lVar9 + 1;
              } while (lVar11 < 4);
              uVar14 = local_res10[3] + 4;
              lVar9 = (longlong)(int)DAT_1405e7b3c * 0x480;
              (&DAT_1405e7b40)[lVar9] = local_447[0];
              (&DAT_1405e7b41)[lVar9] = local_res10[1];
              (&DAT_1405e7b42)[lVar9] = local_res10[2];
              (&DAT_1405e7b43)[lVar9] = local_res10[3];
            }
            else {
              uVar16 = 3;
              do {
                if ((longlong)uVar12 < lVar9 + 1) {
                  return 0xffffffff;
                }
                local_res10[lVar9 + 1] = local_447[lVar9 + 1];
                lVar11 = lVar9 + 2;
                lVar9 = lVar9 + 1;
              } while (lVar11 < 3);
              uVar14 = ((int)(char)local_447[0] & 0x7fU) + 3;
              lVar9 = (longlong)(int)DAT_1405e7b3c * 0x480;
              (&DAT_1405e7b40)[lVar9] = local_447[0];
              (&DAT_1405e7b41)[lVar9] = local_res10[1];
              (&DAT_1405e7b42)[lVar9] = local_res10[2];
            }
            uVar17 = (ulonglong)(int)uVar3;
            if (DAT_1405a7338 != 0) {
              FUN_1400a1378(1);
              FUN_1400a1318();
              cVar20 = (&DAT_1405e7b42)[(longlong)(int)DAT_1405e7b3c * 0x480];
              sVar8 = strlen(&DAT_1405a5df0);
              sprintf(&DAT_1405a5df0 + sVar8,"GetKW2K rcv packet %d[%X]: ",(ulonglong)DAT_1405e7b3c,
                      (ulonglong)(uint)(int)cVar20);
              uVar17 = (ulonglong)DAT_1405e7b3c;
            }
            lVar9 = 0;
            uVar12 = uVar16;
            iVar6 = DAT_1405a7338;
            while( true ) {
              do {
                bVar10 = local_447[uVar16];
                uVar4 = (uint)(char)bVar10;
                uVar3 = (int)uVar12 + 1;
                uVar12 = (ulonglong)uVar3;
                lVar11 = ((longlong)(int)uVar17 * 0x480 - lVar9) + uVar16;
                uVar16 = uVar16 + 1;
                (&DAT_1405e7b40)[lVar11] = bVar10;
                if (iVar6 != 0) {
                  sprintf(&DAT_1405a59f0,"%02X ",(ulonglong)bVar10);
                  FUN_140156b40(&DAT_1405a5df0,&DAT_1405a59f0);
                  uVar17 = (ulonglong)DAT_1405e7b3c;
                  iVar6 = DAT_1405a7338;
                }
                iVar5 = local_res18;
                if (uVar16 == 0x480) goto LAB_14009f4f3;
              } while (uVar3 != uVar14);
              if (uVar13 == uVar14) break;
              lVar9 = lVar9 + uVar16;
              DAT_1405e7b3c = (int)uVar17 + 1;
              uVar17 = (ulonglong)DAT_1405e7b3c;
            }
LAB_14009f4f3:
            cVar20 = (char)local_res8;
            iVar15 = local_res20;
            iVar18 = local_458;
            if (iVar6 != 0) {
              FUN_1400a1378(1);
              uVar17 = (ulonglong)DAT_1405e7b3c;
              iVar15 = local_res20;
            }
            goto LAB_14009f533;
          }
          uVar3 = 5000;
          if (DAT_1405a4bc1 == '\x1a') {
            uVar3 = 7000;
          }
          lVar9 = FUN_14011b248(&DAT_140701ec0);
          if ((longlong)(int)uVar3 < (longlong)(lVar9 - (ulonglong)DAT_140631ecc)) {
            if (DAT_1405a7338 != 0) {
              FUN_1400a1378(1);
              FUN_1400a1318();
              sVar8 = strlen(&DAT_1405a5df0);
              sprintf(&DAT_1405a5df0 + sVar8,"More than %ds since last req, aborting reception",
                      (ulonglong)uVar3);
            }
            return 0xffffffff;
          }
          if ((int)DAT_1405e7b3c < 1) {
            return 0xffffffff;
          }
          DAT_1405e7b3c = DAT_1405e7b3c - 1;
          local_res18 = 0;
          iVar5 = (**(code **)(*DAT_140631e78 + 0xf8))(DAT_140631e78,1);
          if (iVar5 == 0) {
LAB_14009f68b:
            return (ulonglong)DAT_1405a7328;
          }
          cVar20 = '\x01';
          local_res8 = 1;
          iVar5 = 0;
        }
        uVar17 = (ulonglong)DAT_1405e7b3c;
      }
      else {
        if (DAT_140631ec4 != 0) goto LAB_14009f0f0;
LAB_14009f635:
        uVar4 = FUN_1400a11a8(1);
        local_res20 = iVar15 + 1;
        DAT_1401f6e4c = 100;
        if (uVar4 == 0xffffff9c) {
          bVar21 = SBORROW4(local_res20,2);
          iVar6 = iVar15 + -1;
          iVar15 = local_res20;
          if (local_res20 != 2) goto LAB_14009f672;
          if ((longlong)iVar18 - 1U < 3) {
            iVar15 = 0;
            FUN_1400a02b8();
            bVar21 = false;
            iVar6 = -2;
            goto LAB_14009f672;
          }
          goto LAB_14009f674;
        }
        local_res10[0] = (byte)uVar4;
        local_458 = 0;
        if ((((uVar4 & 0xc0) != 0x80) && ((uVar4 & 0xc0) != 0xc0)) &&
           ((local_res10[0] != 0 || ((int)DAT_1405e7b3c < 1)))) break;
        if ((local_res10[0] == 0) && (0 < (int)DAT_1405e7b3c)) goto LAB_14009f683;
        lVar9 = 1;
        if ((uVar4 & 0x3f) == 0) {
          do {
            uVar4 = FUN_1400a11a8(1);
            if (uVar4 == 0xffffff9c) {
              return 0xffffffff;
            }
            local_res10[lVar9] = (byte)uVar4;
            lVar9 = lVar9 + 1;
            uVar12 = 4;
          } while (lVar9 < 4);
          uVar17 = (ulonglong)(int)DAT_1405e7b3c;
          uVar3 = local_res10[3] + 5;
          lVar9 = uVar17 * 0x480;
          (&DAT_1405e7b40)[lVar9] = local_res10[0];
          (&DAT_1405e7b41)[lVar9] = local_res10[1];
          (&DAT_1405e7b42)[lVar9] = local_res10[2];
          (&DAT_1405e7b43)[lVar9] = local_res10[3];
          uVar16 = uVar12;
        }
        else {
          do {
            uVar4 = FUN_1400a11a8(1);
            if (uVar4 == 0xffffff9c) {
              return 0xffffffff;
            }
            local_res10[lVar9] = (byte)uVar4;
            lVar9 = lVar9 + 1;
            uVar12 = 3;
          } while (lVar9 < 3);
          uVar17 = (ulonglong)(int)DAT_1405e7b3c;
          uVar3 = ((int)(char)local_res10[0] & 0x3fU) + 4;
          lVar9 = uVar17 * 0x480;
          (&DAT_1405e7b40)[lVar9] = local_res10[0];
          (&DAT_1405e7b41)[lVar9] = local_res10[1];
          (&DAT_1405e7b42)[lVar9] = local_res10[2];
          uVar16 = uVar12;
        }
        do {
          uVar13 = (uint)uVar12;
          if ((int)uVar3 <= (int)uVar13) break;
          uVar4 = FUN_1400a11a8(1);
          if (uVar4 == 0xffffff9c) {
            uVar17 = (ulonglong)DAT_1405e7b3c;
            break;
          }
          uVar17 = (ulonglong)(int)DAT_1405e7b3c;
          uVar13 = uVar13 + 1;
          uVar12 = (ulonglong)uVar13;
          lVar9 = uVar17 * 0x480 + uVar16;
          uVar16 = uVar16 + 1;
          (&DAT_1405e7b40)[lVar9] = (char)uVar4;
        } while (uVar16 != 0x480);
        if (uVar13 != uVar3) {
          return 0xfffffffe;
        }
        lVar9 = (longlong)(int)uVar17 * 0x480;
        cVar1 = FUN_1400a0c9c(&DAT_1405e7b40 + lVar9);
        iVar15 = local_res20;
        iVar18 = 0;
        if (cVar1 != *(char *)((longlong)&DAT_1405e7b3c + lVar9 + uVar16 + 3)) {
          return 0xfffffffd;
        }
      }
LAB_14009f533:
      uVar3 = (uint)uVar17;
      if (((DAT_1401f6818 == 0x33) && (DAT_140631ec4 != 0)) &&
         ((DAT_1405a4bc1 != '\x01' || (DAT_1405a4bc2 != '\0')))) {
        lVar9 = (longlong)(int)uVar3 * 0x480 + (longlong)(int)uVar4;
        cVar1 = (&DAT_1405e7b43)[lVar9];
        while (((cVar1 == 'A' &&
                (uVar3 = (uint)uVar17, DAT_1405e7b3c = uVar3, (&DAT_1405e7b44)[lVar9] == '\0')) &&
               (uVar3 != 0))) {
          uVar3 = uVar3 - 1;
          lVar9 = (longlong)(int)uVar3 * 0x480 + (longlong)(int)uVar4;
          cVar1 = (&DAT_1405e7b43)[lVar9];
          DAT_1405e7b3c = uVar3;
          uVar17 = (ulonglong)uVar3;
        }
      }
      iVar6 = FUN_14009ff40(&DAT_1405e7b40 + (longlong)(int)uVar3 * 0x480);
      uVar12 = (ulonglong)DAT_1405e7b3c;
      iVar19 = 1;
      if (DAT_1401f6818 == 0x31) {
        if ((int)DAT_1405e7b3c < 1) {
          iVar6 = 2;
        }
        if (((DAT_1405e7b43 == '\x7f') && (DAT_1405e7b45 == 'x')) &&
           ((DAT_1405e7fc3 == '\x7f' && ((DAT_1405e7fc5 == 'x' && ((int)DAT_1405e7b3c < 3)))))) {
          iVar6 = 2;
        }
      }
      if (iVar6 == 0) {
        iVar19 = 0;
        local_res18 = 0;
        if (cVar20 != '\0') {
LAB_14009f892:
          DAT_1405e7b3c = DAT_1405e7b3c + 1;
          uVar12 = (ulonglong)DAT_1405e7b3c;
        }
      }
      else {
        if (iVar6 == 1) {
          (*DAT_14018c720)(1);
        }
        else {
          if (iVar6 == 2) {
            local_res18 = 1;
            goto LAB_14009f892;
          }
          iVar19 = iVar5;
          if (iVar6 != 3) goto LAB_14009f89a;
          (*DAT_14018c720)(1);
          iVar18 = iVar18 + 1;
          local_458 = iVar18;
        }
        uVar12 = (ulonglong)DAT_1405e7b3c;
        local_res18 = 1;
        iVar19 = 1;
      }
LAB_14009f89a:
      bVar21 = DAT_1405a554c == 2;
      uVar4 = (uint)bVar21;
      iVar5 = iVar19;
      if ((DAT_140631ec4 != 0) && (DAT_1401f6818 == 0x33)) {
        lVar9 = FUN_14011b248(&DAT_140701ec0);
        if ((longlong)(lVar9 - (uVar7 & 0xffffffff)) < 0x1f5) {
          uVar12 = (ulonglong)DAT_1405e7b3c;
        }
        else {
          uVar12 = (ulonglong)(int)DAT_1405e7b3c;
          uVar3 = (uint)bVar21;
          if (iVar6 == 1) {
            lVar9 = uVar12 * 0x480 + (longlong)(int)uVar3;
            if ((&DAT_1405e7b43)[lVar9] == 'A') {
              if ((&DAT_1405e7b44)[lVar9] != '\0') goto LAB_14009f92c;
              if ((DAT_1405a4bc1 == '\x01') && (DAT_1405a4bc2 == '\0')) goto LAB_14009f991;
              goto LAB_14009f970;
            }
          }
          else {
LAB_14009f92c:
            if (((((iVar6 == 2) &&
                  (lVar9 = uVar12 * 0x480 + -0x480 + (longlong)(int)uVar3,
                  (&DAT_1405e7b43)[lVar9] == 'A')) && ((&DAT_1405e7b44)[lVar9] == '\0')) &&
                ((DAT_1405a4bc1 == '\x01' && (DAT_1405a4bc2 == '\0')))) && (2 < (int)DAT_1405e7b3c))
            {
LAB_14009f970:
              DAT_1405e7b3c = DAT_1405e7b3c - 1;
              uVar12 = (ulonglong)DAT_1405e7b3c;
              local_res18 = 0;
              iVar5 = 0;
            }
          }
        }
      }
LAB_14009f991:
    } while ((int)uVar12 != 0x100);
    uVar7 = 0xfffffff6;
  }
  return uVar7;
LAB_14009f672:
  if (bVar21 == iVar6 < 0) {
LAB_14009f674:
    if ((int)DAT_1405e7b3c < 1) {
      return 0xffffffff;
    }
    goto LAB_14009f683;
  }
  goto LAB_14009f635;
}


```


### String `"\"VCDSScan.exe\""` at `1401b0f50`
- *(No direct XREFs found)*

### String `"\"support@ross-tech.com\""` at `1401b0f78`
- **XREF from:** `140112b81` in Function **`FUN_140112640`** (`140112640`)
- **XREF from:** `140112b90` in Function **`FUN_140112640`** (`140112640`)

### String `"\"http://www.ross-tech.com/vcds/download/current.html\""` at `1401b15f0`
- *(No direct XREFs found)*

### String `"\"Engine: \""` at `1401b2ee8`
- **XREF from:** `1400bffe3` in Function **`FUN_1400b766c`** (`1400b766c`)

```c
// Function: FUN_1400b766c @ 1400b766c

/* WARNING: Removing unreachable block (ram,0x0001400bf296) */
/* WARNING: Globals starting with '_' overlap smaller symbols at the same address */

undefined8
FUN_1400b766c(undefined8 param_1,undefined8 param_2,longlong param_3,undefined8 *param_4,int param_5
             ,byte *param_6,char param_7)

{
  int *piVar1;
  bool bVar2;
  undefined1 auVar3 [16];
  undefined1 auVar4 [16];
  undefined1 auVar5 [16];
  undefined1 auVar6 [16];
  int iVar7;
  longlong *plVar8;
  longlong lVar9;
  undefined8 *puVar10;
  size_t sVar11;
  undefined1 uVar12;
  uint uVar13;
  undefined8 *puVar14;
  undefined8 *puVar15;
  longlong lVar16;
  int iVar17;
  ulonglong uVar18;
  byte bVar19;
  byte bVar20;
  byte bVar21;
  uint uVar22;
  byte *pbVar23;
  char cVar24;
  longlong lVar25;
  longlong lVar26;
  int iVar27;
  double dVar28;
  double dVar29;
  undefined1 *puVar30;
  code *pcVar31;
  undefined4 uVar33;
  undefined8 uVar32;
  longlong local_e8;
  longlong local_e0;
  undefined8 *local_d8;
  undefined8 local_d0;
  undefined1 local_c8 [8];
  undefined1 local_c0 [8];
  undefined1 local_b8 [8];
  undefined1 local_b0 [8];
  undefined1 local_a8 [8];
  undefined1 local_a0 [8];
  undefined1 local_98 [8];
  undefined8 local_90;
  int local_88;
  longlong local_80;
  longlong local_78;
  undefined1 *local_70;
  longlong local_68;
  undefined8 local_60;
  
  local_60 = 0xfffffffffffffffe;
  lVar25 = 4;
  _eh_vector_constructor_iterator_(&local_d0,8,4,FUN_140001730,FUN_1400b2e64);
  pcVar31 = FUN_1400b2e64;
  _eh_vector_constructor_iterator_(local_b0,8,4,FUN_140001730,FUN_1400b2e64);
  plVar8 = (longlong *)FUN_14013a630();
  if (plVar8 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  local_e0 = (**(code **)(*plVar8 + 0x18))(plVar8);
  local_e0 = local_e0 + 0x18;
  plVar8 = (longlong *)FUN_14013a630();
  if (plVar8 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  lVar9 = (**(code **)(*plVar8 + 0x18))(plVar8);
  lVar9 = lVar9 + 0x18;
  local_e8 = lVar9;
  plVar8 = (longlong *)FUN_14013a630();
  if (plVar8 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  puVar10 = (undefined8 *)(**(code **)(*plVar8 + 0x18))(plVar8);
  puVar15 = puVar10 + 3;
  local_d8 = puVar15;
  sVar11 = strlen("");
  FUN_140001a34(&local_e0,&DAT_14019aa80,sVar11 & 0xffffffff);
  puVar14 = param_4;
  do {
    uVar32 = *(undefined8 *)((param_3 - (longlong)param_4) + (longlong)puVar14);
    sVar11 = strlen("");
    FUN_140001a34(uVar32,&DAT_14019aa80,sVar11 & 0xffffffff);
    uVar32 = *puVar14;
    strlen("");
    FUN_140001a34(uVar32,&DAT_14019aa80);
    uVar33 = (undefined4)((ulonglong)pcVar31 >> 0x20);
    puVar14 = puVar14 + 1;
    lVar25 = lVar25 + -1;
  } while (lVar25 != 0);
  uVar13 = 1;
  switch(param_5) {
  case 0:
    LOCK();
    piVar1 = (int *)(puVar10 + 2);
    iVar17 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar17 + -1 < 1) {
      (**(code **)(*(longlong *)*puVar10 + 8))();
    }
    LOCK();
    piVar1 = (int *)(local_e8 + -8);
    iVar17 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar17 + -1 < 1) {
      (**(code **)(**(longlong **)(local_e8 + -0x18) + 8))();
    }
    LOCK();
    piVar1 = (int *)(local_e0 + -8);
    iVar17 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar17 + -1 < 1) {
      (**(code **)(**(longlong **)(local_e0 + -0x18) + 8))();
    }
    _eh_vector_destructor_iterator_(local_b0,8,4,FUN_1400b2e64);
    _eh_vector_destructor_iterator_(&local_d0,8,4,FUN_1400b2e64);
    return 0xffffffff;
  case 1:
    LOCK();
    piVar1 = (int *)(puVar10 + 2);
    iVar17 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar17 + -1 < 1) {
      (**(code **)(*(longlong *)*puVar10 + 8))();
    }
    LOCK();
    piVar1 = (int *)(local_e8 + -8);
    iVar17 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar17 + -1 < 1) {
      (**(code **)(**(longlong **)(local_e8 + -0x18) + 8))();
    }
    LOCK();
    piVar1 = (int *)(local_e0 + -8);
    iVar17 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar17 + -1 < 1) {
      (**(code **)(**(longlong **)(local_e0 + -0x18) + 8))();
    }
    _eh_vector_destructor_iterator_(local_b0,8,4,FUN_1400b2e64);
    _eh_vector_destructor_iterator_(&local_d0,8,4,FUN_1400b2e64);
    return 0xffffffff;
  case 2:
    bVar19 = *param_6;
    if ((bVar19 & 0xc0) == 0) {
      uVar12 = 0x50;
    }
    else {
      uVar12 = 0x43;
      if ((bVar19 & 0xc0) != 0x40) {
        uVar12 = 0x55;
      }
    }
    FUN_140003cf0(&local_d0,"%c%02X%02X",uVar12,bVar19 & 0x3f,CONCAT44(uVar33,(uint)param_6[1]));
    lVar25 = FUN_14013a61c(0x2c0);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2c0);
    }
    break;
  case 3:
    puVar15 = &local_d0;
    lVar25 = 2;
    do {
      bVar19 = *param_6 & 0x1f;
      if ((*param_6 & 0x1f) == 0) {
        lVar9 = FUN_14013a61c(0x517);
        if (lVar9 != 0) {
          FUN_1400018a8(puVar15,lVar9,0x517);
        }
      }
      else if (bVar19 == 1) {
        lVar9 = FUN_14013a61c(0x2c2);
        if (lVar9 != 0) {
          FUN_1400018a8(puVar15,lVar9,0x2c2);
        }
        lVar9 = FUN_14013a61c(0x2c3);
        if (lVar9 != 0) {
          FUN_1400018a8(puVar15 + 1,lVar9,0x2c3);
        }
      }
      else if (bVar19 == 2) {
        lVar9 = FUN_14013a61c(0x2c4);
        if (lVar9 != 0) {
          FUN_1400018a8(puVar15,lVar9,0x2c4);
        }
        lVar9 = FUN_14013a61c(0x2c5);
        if (lVar9 != 0) {
          FUN_1400018a8(puVar15 + 1,lVar9,0x2c5);
        }
      }
      else if (bVar19 == 4) {
        lVar9 = FUN_14013a61c(0x2e2);
        if (lVar9 != 0) {
          FUN_1400018a8(puVar15,lVar9,0x2e2);
        }
        lVar9 = FUN_14013a61c(0x2e3);
        if (lVar9 != 0) {
          FUN_1400018a8(puVar15 + 1,lVar9,0x2e3);
        }
      }
      else if (bVar19 == 8) {
        lVar9 = FUN_14013a61c(0x2e4);
        if (lVar9 != 0) {
          FUN_1400018a8(puVar15,lVar9,0x2e4);
        }
        lVar9 = FUN_14013a61c(0x2e5);
        if (lVar9 != 0) {
          FUN_1400018a8(puVar15 + 1,lVar9,0x2e5);
        }
      }
      else if (bVar19 == 0x10) {
        lVar9 = FUN_14013a61c(0x2e6);
        if (lVar9 != 0) {
          FUN_1400018a8(puVar15,lVar9,0x2e6);
        }
        lVar9 = FUN_14013a61c(0x2e7);
        if (lVar9 != 0) {
          FUN_1400018a8(puVar15 + 1,lVar9,0x2e7);
        }
      }
      else {
        lVar9 = FUN_14013a61c(0x15e);
        if (lVar9 != 0) {
          FUN_1400018a8(puVar15,lVar9,0x15e);
        }
      }
      param_6 = param_6 + 1;
      puVar15 = puVar15 + 2;
      lVar25 = lVar25 + -1;
    } while (lVar25 != 0);
    lVar25 = FUN_14013a61c(0x2c1);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2c1);
    }
    break;
  case 4:
    sVar11 = strlen("%");
    FUN_140001a34(local_b0,&DAT_1401ab59c,sVar11 & 0xffffffff);
    FUN_140003cf0(&local_d0,"%1.1f",(double)*param_6 * _DAT_1401bdfe8 * DAT_1401bdf00);
    lVar25 = FUN_14013a61c(0x2c7);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2c7);
    }
    break;
  case 5:
    FUN_140003cf0(&local_d0,"%1.0f",(double)(int)(*param_6 - 0x28));
    sVar11 = strlen(&DAT_1401ab434);
    FUN_140001a34(local_b0,&DAT_1401ab434,sVar11 & 0xffffffff);
    lVar25 = FUN_14013a61c(0x2c8);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2c8);
    }
    break;
  case 6:
    lVar25 = FUN_14013a61c(0x2c9);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2c9);
    }
    sVar11 = strlen("%");
    FUN_140001a34(local_b0,&DAT_1401ab59c,sVar11 & 0xffffffff);
    FUN_140003cf0(&local_d0,"%1.1f",(double)(int)(*param_6 - 0x80) * DAT_1401bdf00 * DAT_1401c1710);
    break;
  case 7:
    lVar25 = FUN_14013a61c(0x2ca);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2ca);
    }
    sVar11 = strlen("%");
    FUN_140001a34(local_b0,&DAT_1401ab59c,sVar11 & 0xffffffff);
    FUN_140003cf0(&local_d0,"%1.1f",(double)(int)(*param_6 - 0x80) * DAT_1401bdf00 * DAT_1401c1710);
    break;
  case 8:
    lVar25 = FUN_14013a61c(0x2cb);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2cb);
    }
    sVar11 = strlen("%");
    FUN_140001a34(local_b0,&DAT_1401ab59c,sVar11 & 0xffffffff);
    FUN_140003cf0(&local_d0,"%1.1f",(double)(int)(*param_6 - 0x80) * DAT_1401bdf00 * DAT_1401c1710);
    break;
  case 9:
    lVar25 = FUN_14013a61c(0x2cc);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2cc);
    }
    sVar11 = strlen("%");
    FUN_140001a34(local_b0,&DAT_1401ab59c,sVar11 & 0xffffffff);
    FUN_140003cf0(&local_d0,"%1.1f",(double)(int)(*param_6 - 0x80) * DAT_1401bdf00 * DAT_1401c1710);
    break;
  case 10:
    lVar25 = FUN_14013a61c(0x2cd);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2cd);
    }
    FUN_140003cf0(&local_d0,"%1.0f",(double)((uint)*param_6 + (uint)*param_6 * 2));
    sVar11 = strlen("kPa rel");
    FUN_140001a34(local_b0,"kPa rel",sVar11 & 0xffffffff);
    break;
  case 0xb:
    lVar25 = FUN_14013a61c(0x2ce);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2ce);
    }
    FUN_140003cf0(&local_d0,"%1.0f",(double)*param_6);
    sVar11 = strlen("kPa abs");
    FUN_140001a34(local_b0,"kPa abs",sVar11 & 0xffffffff);
    break;
  case 0xc:
    lVar25 = FUN_14013a61c(0x2cf);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2cf);
    }
    FUN_140003cf0(&local_d0,"%1.0f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * _DAT_1401c1708);
    sVar11 = strlen("/min");
    FUN_140001a34(local_b0,&DAT_1401ab594,sVar11 & 0xffffffff);
    break;
  case 0xd:
    lVar25 = FUN_14013a61c(0x2d0);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2d0);
    }
    FUN_140003cf0(&local_d0,"%1.0f",(double)*param_6);
    sVar11 = strlen("km/h");
    FUN_140001a34(local_b0,&DAT_14019cfcc,sVar11 & 0xffffffff);
    break;
  case 0xe:
    lVar25 = FUN_14013a61c(0x2d1);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2d1);
    }
    lVar25 = FUN_14013a61c(0x122);
    if (lVar25 != 0) {
      FUN_1400018a8(local_b0,lVar25,0x122);
    }
    FUN_140003cf0(&local_d0,"%1.1f",((double)*param_6 - _DAT_1401be038) * _DAT_1401bdef0);
    break;
  case 0xf:
    lVar25 = FUN_14013a61c(0x2d2);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2d2);
    }
    FUN_140003cf0(&local_d0,"%1.0f",(double)(int)(*param_6 - 0x28));
    sVar11 = strlen(&DAT_1401ab434);
    FUN_140001a34(local_b0,&DAT_1401ab434,sVar11 & 0xffffffff);
    break;
  case 0x10:
    lVar25 = FUN_14013a61c(0x2d3);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2d3);
    }
    FUN_140003cf0(&local_d0,"%1.2f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * DAT_1401bdfd8);
    sVar11 = strlen("g/s");
    FUN_140001a34(local_b0,&DAT_1401ab574,sVar11 & 0xffffffff);
    break;
  case 0x11:
    lVar25 = FUN_14013a61c(0x2d4);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2d4);
      lVar9 = local_e8;
    }
    FUN_140003cf0(&local_e0,lVar9,&DAT_14019aa80);
    sVar11 = strlen("%");
    FUN_140001a34(local_b0,&DAT_1401ab59c,sVar11 & 0xffffffff);
    FUN_140003cf0(&local_d0,"%1.1f",(double)*param_6 * _DAT_1401bdfe8 * DAT_1401bdf00);
    break;
  case 0x12:
    bVar19 = *param_6 & 7;
    if (bVar19 == 1) {
      lVar25 = FUN_14013a61c(0x2d6);
      if (lVar25 != 0) {
        FUN_1400018a8(&local_d0,lVar25,0x2d6);
      }
    }
    else if (bVar19 == 2) {
      lVar25 = FUN_14013a61c(0x2d7);
      if (lVar25 != 0) {
        FUN_1400018a8(&local_d0,lVar25,0x2d7);
      }
    }
    else if (bVar19 == 4) {
      lVar25 = FUN_14013a61c(0x2d8);
      if (lVar25 != 0) {
        FUN_1400018a8(&local_d0,lVar25,0x2d8);
      }
    }
    else {
      lVar25 = FUN_14013a61c(0x15e);
      if (lVar25 != 0) {
        FUN_1400018a8(&local_d0,lVar25,0x15e);
      }
    }
    lVar25 = FUN_14013a61c(0x2d5);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2d5);
    }
    sVar11 = strlen("");
    FUN_140001a34(local_b0,&DAT_14019aa80,sVar11 & 0xffffffff);
    break;
  case 0x13:
    lVar25 = FUN_14013a61c(0x2da);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2da);
      lVar9 = local_e8;
    }
    if ((*param_6 & 1) != 0) {
      FUN_140003cf0(&local_d8,lVar9,1,1);
      FUN_14000d230(&local_d0,local_d8,*(undefined4 *)(local_d8 + -2));
    }
    if ((*param_6 & 2) != 0) {
      FUN_140003cf0(&local_d8,lVar9,1,2);
      FUN_14000d230(&local_d0,local_d8,*(undefined4 *)(local_d8 + -2));
    }
    if ((*param_6 & 4) != 0) {
      FUN_140003cf0(&local_d8,lVar9,1,3);
      FUN_14000d230(&local_d0,local_d8,*(undefined4 *)(local_d8 + -2));
    }
    if ((*param_6 & 8) != 0) {
      FUN_140003cf0(&local_d8,lVar9,1,4);
      FUN_14000d230(&local_d0,local_d8,*(undefined4 *)(local_d8 + -2));
    }
    if ((*param_6 & 0x10) != 0) {
      FUN_140003cf0(&local_d8,lVar9,2,1);
      FUN_14000d230(local_c8,local_d8,*(undefined4 *)(local_d8 + -2));
    }
    if ((*param_6 & 0x20) != 0) {
      FUN_140003cf0(&local_d8,lVar9,2,2);
      FUN_14000d230(local_c8,local_d8,*(undefined4 *)(local_d8 + -2));
    }
    if ((*param_6 & 0x40) != 0) {
      FUN_140003cf0(&local_d8,lVar9,2,3);
      FUN_14000d230(local_c8,local_d8,*(undefined4 *)(local_d8 + -2));
    }
    if ((*param_6 & 0x80) != 0) {
      FUN_140003cf0(&local_d8,lVar9,2,4);
      FUN_14000d230(local_c8,local_d8,*(undefined4 *)(local_d8 + -2));
    }
    lVar25 = FUN_14013a61c(0x2d9);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2d9);
    }
    break;
  case 0x14:
    lVar25 = FUN_14013a61c(0x2db);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2db);
      lVar9 = local_e8;
    }
    FUN_140003cf0(&local_e0,lVar9,1,1);
    FUN_140003cf0(&local_d0,"%1.3f",(double)*param_6 * DAT_1401bdfd0);
    sVar11 = strlen("V");
    FUN_140001a34(local_b0,&DAT_1401ab500,sVar11 & 0xffffffff);
    dVar29 = ((double)param_6[1] - _DAT_1401be038) * DAT_1401bdf00 * DAT_1401c1710;
    sVar11 = strlen("");
    FUN_140001a34(&local_e8,&DAT_14019aa80,sVar11 & 0xffffffff);
    if ((0.0 < dVar29) && (lVar25 = FUN_14013a61c(0x2dd), lVar25 != 0)) {
      FUN_1400018a8(&local_e8,lVar25,0x2dd);
    }
    if ((dVar29 < 0.0) && (lVar25 = FUN_14013a61c(0x2dc), lVar25 != 0)) {
      FUN_1400018a8(&local_e8,lVar25,0x2dc);
    }
    FUN_140003cf0(local_c8,"%1.2f",dVar29);
    FUN_140003cf0(local_a8,"%s(%s)",&DAT_1401ab59c,local_e8);
    if (param_6[1] == 0xff) {
      sVar11 = strlen("");
      FUN_140001a34(local_c8,&DAT_14019aa80,sVar11 & 0xffffffff);
      sVar11 = strlen("");
      FUN_140001a34(local_a8,&DAT_14019aa80,sVar11 & 0xffffffff);
    }
    break;
  case 0x15:
    lVar25 = FUN_14013a61c(0x2db);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2db);
      lVar9 = local_e8;
    }
    FUN_140003cf0(&local_e0,lVar9,1,2);
    FUN_140003cf0(&local_d0,"%1.3f",(double)*param_6 * DAT_1401bdfd0);
    sVar11 = strlen("V");
    FUN_140001a34(local_b0,&DAT_1401ab500,sVar11 & 0xffffffff);
    dVar29 = ((double)param_6[1] - _DAT_1401be038) * DAT_1401bdf00 * DAT_1401c1710;
    sVar11 = strlen("");
    FUN_140001a34(&local_e8,&DAT_14019aa80,sVar11 & 0xffffffff);
    if ((0.0 < dVar29) && (lVar25 = FUN_14013a61c(0x2dd), lVar25 != 0)) {
      FUN_1400018a8(&local_e8,lVar25,0x2dd);
    }
    if ((dVar29 < 0.0) && (lVar25 = FUN_14013a61c(0x2dc), lVar25 != 0)) {
      FUN_1400018a8(&local_e8,lVar25,0x2dc);
    }
    FUN_140003cf0(local_c8,"%1.2f",dVar29);
    FUN_140003cf0(local_a8,"%s(%s)",&DAT_1401ab59c,local_e8);
    if (param_6[1] == 0xff) {
      sVar11 = strlen("");
      FUN_140001a34(local_c8,&DAT_14019aa80,sVar11 & 0xffffffff);
      sVar11 = strlen("");
      FUN_140001a34(local_a8,&DAT_14019aa80,sVar11 & 0xffffffff);
    }
    break;
  case 0x16:
    lVar25 = FUN_14013a61c(0x2db);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2db);
      lVar9 = local_e8;
    }
    if (param_7 == '\0') {
      FUN_140003cf0(&local_e0,lVar9,2,1);
    }
    else {
      FUN_140003cf0(&local_e0,lVar9,1,3);
    }
    FUN_140003cf0(&local_d0,"%1.3f",(double)*param_6 * DAT_1401bdfd0);
    sVar11 = strlen("V");
    FUN_140001a34(local_b0,&DAT_1401ab500,sVar11 & 0xffffffff);
    dVar29 = ((double)param_6[1] - _DAT_1401be038) * DAT_1401bdf00 * DAT_1401c1710;
    sVar11 = strlen("");
    FUN_140001a34(&local_e8,&DAT_14019aa80,sVar11 & 0xffffffff);
    if ((0.0 < dVar29) && (lVar25 = FUN_14013a61c(0x2dd), lVar25 != 0)) {
      FUN_1400018a8(&local_e8,lVar25,0x2dd);
    }
    if ((dVar29 < 0.0) && (lVar25 = FUN_14013a61c(0x2dc), lVar25 != 0)) {
      FUN_1400018a8(&local_e8,lVar25,0x2dc);
    }
    FUN_140003cf0(local_c8,"%1.2f",dVar29);
    FUN_140003cf0(local_a8,"%s(%s)",&DAT_1401ab59c,local_e8);
    if (param_6[1] == 0xff) {
      sVar11 = strlen("");
      FUN_140001a34(local_c8,&DAT_14019aa80,sVar11 & 0xffffffff);
      sVar11 = strlen("");
      FUN_140001a34(local_a8,&DAT_14019aa80,sVar11 & 0xffffffff);
    }
    break;
  case 0x17:
    lVar25 = FUN_14013a61c(0x2db);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2db);
      lVar9 = local_e8;
    }
    if (param_7 == '\0') {
      FUN_140003cf0(&local_e0,lVar9,2,2);
    }
    else {
      FUN_140003cf0(&local_e0,lVar9,1,4);
    }
    FUN_140003cf0(&local_d0,"%1.3f",(double)*param_6 * DAT_1401bdfd0);
    sVar11 = strlen("V");
    FUN_140001a34(local_b0,&DAT_1401ab500,sVar11 & 0xffffffff);
    dVar29 = ((double)param_6[1] - _DAT_1401be038) * DAT_1401bdf00 * DAT_1401c1710;
    sVar11 = strlen("");
    FUN_140001a34(&local_e8,&DAT_14019aa80,sVar11 & 0xffffffff);
    if ((0.0 < dVar29) && (lVar25 = FUN_14013a61c(0x2dd), lVar25 != 0)) {
      FUN_1400018a8(&local_e8,lVar25,0x2dd);
    }
    if ((dVar29 < 0.0) && (lVar25 = FUN_14013a61c(0x2dc), lVar25 != 0)) {
      FUN_1400018a8(&local_e8,lVar25,0x2dc);
    }
    FUN_140003cf0(local_c8,"%1.2f",dVar29);
    FUN_140003cf0(local_a8,"%s(%s)",&DAT_1401ab59c,local_e8);
    if (param_6[1] == 0xff) {
      sVar11 = strlen("");
      FUN_140001a34(local_c8,&DAT_14019aa80,sVar11 & 0xffffffff);
      sVar11 = strlen("");
      FUN_140001a34(local_a8,&DAT_14019aa80,sVar11 & 0xffffffff);
    }
    break;
  case 0x18:
    lVar25 = FUN_14013a61c(0x2db);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2db);
      lVar9 = local_e8;
    }
    if (param_7 == '\0') {
      FUN_140003cf0(&local_e0,lVar9,3,1);
    }
    else {
      FUN_140003cf0(&local_e0,lVar9,2,1);
    }
    FUN_140003cf0(&local_d0,"%1.3f",(double)*param_6 * DAT_1401bdfd0);
    sVar11 = strlen("V");
    FUN_140001a34(local_b0,&DAT_1401ab500,sVar11 & 0xffffffff);
    dVar29 = ((double)param_6[1] - _DAT_1401be038) * DAT_1401bdf00 * DAT_1401c1710;
    sVar11 = strlen("");
    FUN_140001a34(&local_e8,&DAT_14019aa80,sVar11 & 0xffffffff);
    if ((0.0 < dVar29) && (lVar25 = FUN_14013a61c(0x2dd), lVar25 != 0)) {
      FUN_1400018a8(&local_e8,lVar25,0x2dd);
    }
    if ((dVar29 < 0.0) && (lVar25 = FUN_14013a61c(0x2dc), lVar25 != 0)) {
      FUN_1400018a8(&local_e8,lVar25,0x2dc);
    }
    FUN_140003cf0(local_c8,"%1.2f",dVar29);
    FUN_140003cf0(local_a8,"%s(%s)",&DAT_1401ab59c,local_e8);
    if (param_6[1] == 0xff) {
      sVar11 = strlen("");
      FUN_140001a34(local_c8,&DAT_14019aa80,sVar11 & 0xffffffff);
      sVar11 = strlen("");
      FUN_140001a34(local_a8,&DAT_14019aa80,sVar11 & 0xffffffff);
    }
    break;
  case 0x19:
    lVar25 = FUN_14013a61c(0x2db);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2db);
      lVar9 = local_e8;
    }
    if (param_7 == '\0') {
      FUN_140003cf0(&local_e0,lVar9,3);
    }
    else {
      FUN_140003cf0(&local_e0,lVar9,2,2);
    }
    FUN_140003cf0(&local_d0,"%1.3f",(double)*param_6 * DAT_1401bdfd0);
    sVar11 = strlen("V");
    FUN_140001a34(local_b0,&DAT_1401ab500,sVar11 & 0xffffffff);
    dVar29 = ((double)param_6[1] - _DAT_1401be038) * DAT_1401bdf00 * DAT_1401c1710;
    sVar11 = strlen("");
    FUN_140001a34(&local_e8,&DAT_14019aa80,sVar11 & 0xffffffff);
    if ((0.0 < dVar29) && (lVar25 = FUN_14013a61c(0x2dd), lVar25 != 0)) {
      FUN_1400018a8(&local_e8,lVar25,0x2dd);
    }
    if ((dVar29 < 0.0) && (lVar25 = FUN_14013a61c(0x2dc), lVar25 != 0)) {
      FUN_1400018a8(&local_e8,lVar25,0x2dc);
    }
    FUN_140003cf0(local_c8,"%1.2f",dVar29);
    FUN_140003cf0(local_a8,"%s(%s)",&DAT_1401ab59c,local_e8);
    if (param_6[1] == 0xff) {
      sVar11 = strlen("");
      FUN_140001a34(local_c8,&DAT_14019aa80,sVar11 & 0xffffffff);
      sVar11 = strlen("");
      FUN_140001a34(local_a8,&DAT_14019aa80,sVar11 & 0xffffffff);
    }
    break;
  case 0x1a:
    lVar25 = FUN_14013a61c(0x2db);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2db);
      lVar9 = local_e8;
    }
    if (param_7 == '\0') {
      FUN_140003cf0(&local_e0,lVar9,4,1);
    }
    else {
      FUN_140003cf0(&local_e0,lVar9,2);
    }
    FUN_140003cf0(&local_d0,"%1.3f",(double)*param_6 * DAT_1401bdfd0);
    sVar11 = strlen("V");
    FUN_140001a34(local_b0,&DAT_1401ab500,sVar11 & 0xffffffff);
    dVar29 = ((double)param_6[1] - _DAT_1401be038) * DAT_1401bdf00 * DAT_1401c1710;
    sVar11 = strlen("");
    FUN_140001a34(&local_e8,&DAT_14019aa80,sVar11 & 0xffffffff);
    if ((0.0 < dVar29) && (lVar25 = FUN_14013a61c(0x2dd), lVar25 != 0)) {
      FUN_1400018a8(&local_e8,lVar25,0x2dd);
    }
    if ((dVar29 < 0.0) && (lVar25 = FUN_14013a61c(0x2dc), lVar25 != 0)) {
      FUN_1400018a8(&local_e8,lVar25,0x2dc);
    }
    FUN_140003cf0(local_c8,"%1.2f",dVar29);
    FUN_140003cf0(local_a8,"%s(%s)",&DAT_1401ab59c,local_e8);
    if (param_6[1] == 0xff) {
      sVar11 = strlen("");
      FUN_140001a34(local_c8,&DAT_14019aa80,sVar11 & 0xffffffff);
      sVar11 = strlen("");
      FUN_140001a34(local_a8,&DAT_14019aa80,sVar11 & 0xffffffff);
    }
    break;
  case 0x1b:
    lVar25 = FUN_14013a61c(0x2db);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2db);
      lVar9 = local_e8;
    }
    if (param_7 == '\0') {
      FUN_140003cf0(&local_e0,lVar9,4);
    }
    else {
      FUN_140003cf0(&local_e0,lVar9,2);
    }
    FUN_140003cf0(&local_d0,"%1.3f",(double)*param_6 * DAT_1401bdfd0);
    sVar11 = strlen("V");
    FUN_140001a34(local_b0,&DAT_1401ab500,sVar11 & 0xffffffff);
    dVar29 = ((double)param_6[1] - _DAT_1401be038) * DAT_1401bdf00 * DAT_1401c1710;
    sVar11 = strlen("");
    FUN_140001a34(&local_e8,&DAT_14019aa80,sVar11 & 0xffffffff);
    if ((0.0 < dVar29) && (lVar25 = FUN_14013a61c(0x2dd), lVar25 != 0)) {
      FUN_1400018a8(&local_e8,lVar25,0x2dd);
    }
    if ((dVar29 < 0.0) && (lVar25 = FUN_14013a61c(0x2dc), lVar25 != 0)) {
      FUN_1400018a8(&local_e8,lVar25,0x2dc);
    }
    FUN_140003cf0(local_c8,"%1.2f",dVar29);
    FUN_140003cf0(local_a8,"%s(%s)",&DAT_1401ab59c,local_e8);
    if (param_6[1] == 0xff) {
      sVar11 = strlen("");
      FUN_140001a34(local_c8,&DAT_14019aa80,sVar11 & 0xffffffff);
      sVar11 = strlen("");
      FUN_140001a34(local_a8,&DAT_14019aa80,sVar11 & 0xffffffff);
    }
    break;
  case 0x1c:
    bVar19 = *param_6;
    if (bVar19 < 0x12) {
      if (bVar19 == 0x11) {
        sVar11 = strlen("Eng. Manuf. Diag");
        FUN_140001a34(&local_d0,"Eng. Manuf. Diag",sVar11 & 0xffffffff);
      }
      else if (bVar19 < 10) {
        if (bVar19 == 9) {
          sVar11 = strlen("OBD+OBD II+EOBD");
          FUN_140001a34(&local_d0,"OBD+OBD II+EOBD",sVar11 & 0xffffffff);
        }
        else if (bVar19 == 1) {
          sVar11 = strlen("OBD II (CARB)");
          FUN_140001a34(&local_d0,"OBD II (CARB)",sVar11 & 0xffffffff);
        }
        else if (bVar19 == 2) {
          sVar11 = strlen("OBD (EPA)");
          FUN_140001a34(&local_d0,"OBD (EPA)",sVar11 & 0xffffffff);
        }
        else if (bVar19 == 3) {
          sVar11 = strlen("OBD + OBD II");
          FUN_140001a34(&local_d0,"OBD + OBD II",sVar11 & 0xffffffff);
        }
        else if (bVar19 == 4) {
          sVar11 = strlen("OBD I");
          FUN_140001a34(&local_d0,"OBD I",sVar11 & 0xffffffff);
        }
        else if (bVar19 == 5) {
          lVar25 = FUN_14013a61c(0x196);
          if (lVar25 != 0) {
            FUN_1400018a8(&local_d0,lVar25,0x196);
          }
        }
        else if (bVar19 == 6) {
          sVar11 = strlen("Euro-OBD");
          FUN_140001a34(&local_d0,"Euro-OBD",sVar11 & 0xffffffff);
        }
        else if (bVar19 == 7) {
          sVar11 = strlen("EOBD + OBD II");
          FUN_140001a34(&local_d0,"EOBD + OBD II",sVar11 & 0xffffffff);
        }
        else {
          if (bVar19 != 8) goto LAB_1400b97e1;
          sVar11 = strlen("OBD + EOBD");
          FUN_140001a34(&local_d0,"OBD + EOBD",sVar11 & 0xffffffff);
        }
      }
      else if (bVar19 == 10) {
        sVar11 = strlen("JOBD");
        FUN_140001a34(&local_d0,&DAT_1401b2860,sVar11 & 0xffffffff);
      }
      else if (bVar19 == 0xb) {
        sVar11 = strlen("JOBD + OBD II");
        FUN_140001a34(&local_d0,"JOBD + OBD II",sVar11 & 0xffffffff);
      }
      else if (bVar19 == 0xc) {
        sVar11 = strlen("JOBD + EOBD");
        FUN_140001a34(&local_d0,"JOBD + EOBD",sVar11 & 0xffffffff);
      }
      else if (bVar19 == 0xd) {
        sVar11 = strlen("JOBD+EOBD+OBD II");
        FUN_140001a34(&local_d0,"JOBD+EOBD+OBD II",sVar11 & 0xffffffff);
      }
      else if (bVar19 == 0xe) {
        sVar11 = strlen("HD Euro IV/B1");
        FUN_140001a34(&local_d0,"HD Euro IV/B1",sVar11 & 0xffffffff);
      }
      else if (bVar19 == 0xf) {
        sVar11 = strlen("HD Euro V/B2");
        FUN_140001a34(&local_d0,"HD Euro V/B2",sVar11 & 0xffffffff);
      }
      else {
        if (bVar19 != 0x10) goto LAB_1400b97e1;
        sVar11 = strlen("HD EURO EEC/C");
        FUN_140001a34(&local_d0,"HD EURO EEC/C",sVar11 & 0xffffffff);
      }
    }
    else if (bVar19 < 0x1d) {
      if (bVar19 == 0x1c) {
        sVar11 = strlen("OBDBr-1");
        FUN_140001a34(&local_d0,"OBDBr-1",sVar11 & 0xffffffff);
      }
      else if (bVar19 == 0x12) {
        sVar11 = strlen("Eng. Manuf. Diag +");
        FUN_140001a34(&local_d0,"Eng. Manuf. Diag +",sVar11 & 0xffffffff);
      }
      else if (bVar19 == 0x13) {
        sVar11 = strlen("HD OBD-C");
        FUN_140001a34(&local_d0,"HD OBD-C",sVar11 & 0xffffffff);
      }
      else if (bVar19 == 0x14) {
        sVar11 = strlen("HD OBD");
        FUN_140001a34(&local_d0,"HD OBD",sVar11 & 0xffffffff);
      }
      else if (bVar19 == 0x15) {
        sVar11 = strlen("WWH OBD");
        FUN_140001a34(&local_d0,"WWH OBD",sVar11 & 0xffffffff);
      }
      else if (bVar19 == 0x17) {
        sVar11 = strlen("HD EOBD-I");
        FUN_140001a34(&local_d0,"HD EOBD-I",sVar11 & 0xffffffff);
      }
      else if (bVar19 == 0x18) {
        sVar11 = strlen("HD EOBD-I M");
        FUN_140001a34(&local_d0,"HD EOBD-I M",sVar11 & 0xffffffff);
      }
      else if (bVar19 == 0x19) {
        sVar11 = strlen("HD EOBD-II");
        FUN_140001a34(&local_d0,"HD EOBD-II",sVar11 & 0xffffffff);
      }
      else {
        if (bVar19 != 0x1a) goto LAB_1400b97e1;
        sVar11 = strlen("HD EOBD-II N");
        FUN_140001a34(&local_d0,"HD EOBD-II N",sVar11 & 0xffffffff);
      }
    }
    else if (bVar19 == 0x1d) {
      FUN_140001834(&local_d0,"OBDBr-2");
    }
    else if (bVar19 == 0x1e) {
      FUN_140001834(&local_d0,&DAT_1401b2970);
    }
    else if (bVar19 == 0x1f) {
      FUN_140001834(&local_d0,"IOBD I");
    }
    else if (bVar19 == 0x20) {
      FUN_140001834(&local_d0,"IOBD II");
    }
    else if (bVar19 == 0x21) {
      FUN_140001834(&local_d0,"HD EOBD-VI");
    }
    else if (bVar19 == 0x22) {
      FUN_140001834(&local_d0,"OBD+OBDII+HDOBD");
    }
    else if (bVar19 == 0x23) {
      FUN_140001834(&local_d0,"OBDBr-3");
    }
    else {
LAB_1400b97e1:
      lVar25 = FUN_14013a61c(0x15e);
      if (lVar25 != 0) {
        FUN_1400018a8(&local_d0,lVar25,0x15e);
      }
    }
    FUN_140001834(local_b0,&DAT_14019aa80);
    lVar25 = FUN_14013a61c(0x2de);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2de);
    }
    break;
  case 0x1d:
    lVar25 = FUN_14013a61c(0x2da);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2da);
      lVar9 = local_e8;
    }
    if ((*param_6 & 1) != 0) {
      FUN_140003cf0(&local_d8,lVar9,1,1);
      FUN_14000d230(&local_d0,local_d8,*(undefined4 *)(local_d8 + -2));
    }
    if ((*param_6 & 2) != 0) {
      FUN_140003cf0(&local_d8,lVar9,1,2);
      FUN_14000d230(&local_d0,local_d8,*(undefined4 *)(local_d8 + -2));
    }
    if ((*param_6 & 4) != 0) {
      FUN_140003cf0(&local_d8,lVar9,2,1);
      FUN_14000d230(&local_d0,local_d8,*(undefined4 *)(local_d8 + -2));
    }
    if ((*param_6 & 8) != 0) {
      FUN_140003cf0(&local_d8,lVar9,2,2);
      FUN_14000d230(&local_d0,local_d8,*(undefined4 *)(local_d8 + -2));
    }
    if ((*param_6 & 0x10) != 0) {
      FUN_140003cf0(&local_d8,lVar9,3,1);
      FUN_14000d230(local_c8,local_d8,*(undefined4 *)(local_d8 + -2));
    }
    if ((*param_6 & 0x20) != 0) {
      FUN_140003cf0(&local_d8,lVar9,3,2);
      FUN_14000d230(local_c8,local_d8,*(undefined4 *)(local_d8 + -2));
    }
    if ((*param_6 & 0x40) != 0) {
      FUN_140003cf0(&local_d8,lVar9,4,1);
      FUN_14000d230(local_c8,local_d8,*(undefined4 *)(local_d8 + -2));
    }
    if ((*param_6 & 0x80) != 0) {
      FUN_140003cf0(&local_d8,lVar9,4,2);
      FUN_14000d230(local_c8,local_d8,*(undefined4 *)(local_d8 + -2));
    }
    lVar25 = FUN_14013a61c(0x2d9);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2d9);
    }
    break;
  case 0x1e:
    lVar25 = FUN_14013a61c(0x2df);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2df);
    }
    if ((*param_6 & 1) == 0) {
      lVar25 = FUN_14013a61c(0x2e0);
      if (lVar25 != 0) {
        FUN_1400018a8(&local_d0,lVar25,0x2e0);
      }
    }
    else {
      lVar25 = FUN_14013a61c(0x2e1);
      if (lVar25 != 0) {
        FUN_1400018a8(&local_d0,lVar25,0x2e1);
      }
    }
    FUN_140001834(local_b0,&DAT_14019aa80);
    break;
  case 0x1f:
    lVar25 = FUN_14013a61c(0x2f8);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2f8);
    }
    FUN_140003cf0(&local_d0,"%1.0f",(double)((uint)*param_6 * 0x100 + (uint)param_6[1]));
    FUN_140001834(local_b0,&DAT_1401ab564);
    break;
  default:
    LOCK();
    piVar1 = (int *)(puVar10 + 2);
    iVar17 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar17 + -1 < 1) {
      (**(code **)(*(longlong *)*puVar10 + 8))();
    }
    LOCK();
    piVar1 = (int *)(local_e8 + -8);
    iVar17 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar17 + -1 < 1) {
      (**(code **)(**(longlong **)(local_e8 + -0x18) + 8))();
    }
    LOCK();
    piVar1 = (int *)(local_e0 + -8);
    iVar17 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (iVar17 + -1 < 1) {
      (**(code **)(**(longlong **)(local_e0 + -0x18) + 8))();
    }
    _eh_vector_destructor_iterator_(local_b0,8,4,FUN_1400b2e64);
    _eh_vector_destructor_iterator_(&local_d0,8,4,FUN_1400b2e64);
    return 0xffffffff;
  case 0x21:
    lVar25 = FUN_14013a61c(0x2f9);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2f9);
    }
    FUN_140003cf0(&local_d0,"%1.0f",(double)((uint)*param_6 * 0x100 + (uint)param_6[1]));
    FUN_140001834(local_b0,&DAT_1401b29b0);
    break;
  case 0x22:
    lVar25 = FUN_14013a61c(0x2fa);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2fa);
    }
    FUN_140003cf0(&local_d0,"%1.2f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * _DAT_1401c1700);
    FUN_140001834(local_b0,&DAT_1401b29b4);
    break;
  case 0x23:
    lVar25 = FUN_14013a61c(0x2fb);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2fb);
    }
    FUN_140003cf0(&local_d0,"%1.0f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * DAT_1401bd818);
    FUN_140001834(local_b0,"kPa rel");
    break;
  case 0x24:
    lVar25 = FUN_14013a61c(0x2db);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2db);
      lVar9 = local_e8;
    }
    FUN_140003cf0(&local_e0,lVar9,1,1);
    FUN_140003cf0(&local_d0,"%1.3f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * _DAT_1401c16f8);
    FUN_140001834(local_b0,"Lambda");
    FUN_140003cf0(local_c8,"%1.3f",
                  (double)((uint)param_6[2] * 0x100 + (uint)param_6[3]) * _DAT_1401c16f0);
    FUN_140001834(local_a8,&DAT_1401ab500);
    break;
  case 0x25:
    lVar25 = FUN_14013a61c(0x2db);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2db);
      lVar9 = local_e8;
    }
    FUN_140003cf0(&local_e0,lVar9,1,2);
    FUN_140003cf0(&local_d0,"%1.3f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * _DAT_1401c16f8);
    FUN_140001834(local_b0,"Lambda");
    FUN_140003cf0(local_c8,"%1.3f",
                  (double)((uint)param_6[2] * 0x100 + (uint)param_6[3]) * _DAT_1401c16f0);
    FUN_140001834(local_a8,&DAT_1401ab500);
    break;
  case 0x26:
    lVar25 = FUN_14013a61c(0x2db);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2db);
      lVar9 = local_e8;
    }
    if (param_7 == '\0') {
      FUN_140003cf0(&local_e0,lVar9,2,1);
    }
    else {
      FUN_140003cf0(&local_e0,lVar9,1,3);
    }
    FUN_140003cf0(&local_d0,"%1.3f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * _DAT_1401c16f8);
    FUN_140001834(local_b0,"Lambda");
    FUN_140003cf0(local_c8,"%1.3f",
                  (double)((uint)param_6[2] * 0x100 + (uint)param_6[3]) * _DAT_1401c16f0);
    FUN_140001834(local_a8,&DAT_1401ab500);
    break;
  case 0x27:
    lVar25 = FUN_14013a61c(0x2db);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2db);
      lVar9 = local_e8;
    }
    if (param_7 == '\0') {
      FUN_140003cf0(&local_e0,lVar9,2,2);
    }
    else {
      FUN_140003cf0(&local_e0,lVar9,1,4);
    }
    FUN_140003cf0(&local_d0,"%1.3f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * _DAT_1401c16f8);
    FUN_140001834(local_b0,"Lambda");
    FUN_140003cf0(local_c8,"%1.3f",
                  (double)((uint)param_6[2] * 0x100 + (uint)param_6[3]) * _DAT_1401c16f0);
    FUN_140001834(local_a8,&DAT_1401ab500);
    break;
  case 0x28:
    lVar25 = FUN_14013a61c(0x2db);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2db);
      lVar9 = local_e8;
    }
    if (param_7 == '\0') {
      FUN_140003cf0(&local_e0,lVar9,3,1);
    }
    else {
      FUN_140003cf0(&local_e0,lVar9,2,1);
    }
    FUN_140003cf0(&local_d0,"%1.3f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * _DAT_1401c16f8);
    FUN_140001834(local_b0,"Lambda");
    FUN_140003cf0(local_c8,"%1.3f",
                  (double)((uint)param_6[2] * 0x100 + (uint)param_6[3]) * _DAT_1401c16f0);
    FUN_140001834(local_a8,&DAT_1401ab500);
    break;
  case 0x29:
    lVar25 = FUN_14013a61c(0x2db);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2db);
      lVar9 = local_e8;
    }
    if (param_7 == '\0') {
      FUN_140003cf0(&local_e0,lVar9,3);
    }
    else {
      FUN_140003cf0(&local_e0,lVar9,2,2);
    }
    FUN_140003cf0(&local_d0,"%1.3f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * _DAT_1401c16f8);
    FUN_140001834(local_b0,"Lambda");
    FUN_140003cf0(local_c8,"%1.3f",
                  (double)((uint)param_6[2] * 0x100 + (uint)param_6[3]) * _DAT_1401c16f0);
    FUN_140001834(local_a8,&DAT_1401ab500);
    break;
  case 0x2a:
    lVar25 = FUN_14013a61c(0x2db);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2db);
      lVar9 = local_e8;
    }
    if (param_7 == '\0') {
      FUN_140003cf0(&local_e0,lVar9,4,1);
    }
    else {
      FUN_140003cf0(&local_e0,lVar9,2);
    }
    FUN_140003cf0(&local_d0,"%1.3f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * _DAT_1401c16f8);
    FUN_140001834(local_b0,"Lambda");
    FUN_140003cf0(local_c8,"%1.3f",
                  (double)((uint)param_6[2] * 0x100 + (uint)param_6[3]) * _DAT_1401c16f0);
    FUN_140001834(local_a8,&DAT_1401ab500);
    break;
  case 0x2b:
    lVar25 = FUN_14013a61c(0x2db);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2db);
      lVar9 = local_e8;
    }
    if (param_7 == '\0') {
      FUN_140003cf0(&local_e0,lVar9,4);
    }
    else {
      FUN_140003cf0(&local_e0,lVar9,2);
    }
    FUN_140003cf0(&local_d0,"%1.3f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * _DAT_1401c16f8);
    FUN_140001834(local_b0,"Lambda");
    FUN_140003cf0(local_c8,"%1.3f",
                  (double)((uint)param_6[2] * 0x100 + (uint)param_6[3]) * _DAT_1401c16f0);
    FUN_140001834(local_a8,&DAT_1401ab500);
    break;
  case 0x2c:
    lVar25 = FUN_14013a61c(0x2fc);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2fc);
    }
    FUN_140001834(local_b0,&DAT_1401ab59c);
    FUN_140003cf0(&local_d0,"%1.1f",(double)*param_6 * _DAT_1401bdfe8 * DAT_1401bdf00);
    break;
  case 0x2d:
    lVar25 = FUN_14013a61c(0x2fd);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2fd);
    }
    FUN_140001834(local_b0,&DAT_1401ab59c);
    FUN_140003cf0(&local_d0,"%1.1f",(double)(int)(*param_6 - 0x80) * DAT_1401bdf00 * DAT_1401c1710);
    break;
  case 0x2e:
    lVar25 = FUN_14013a61c(0x2fe);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2fe);
    }
    FUN_140001834(local_b0,&DAT_1401ab59c);
    FUN_140003cf0(&local_d0,"%1.1f",(double)*param_6 * _DAT_1401bdfe8 * DAT_1401bdf00);
    break;
  case 0x2f:
    lVar25 = FUN_14013a61c(0x2ff);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x2ff);
    }
    FUN_140001834(local_b0,&DAT_1401ab59c);
    FUN_140003cf0(&local_d0,"%1.1f",(double)*param_6 * _DAT_1401bdfe8 * DAT_1401bdf00);
    break;
  case 0x30:
    lVar25 = FUN_14013a61c(0x300);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x300);
    }
    FUN_140003cf0(&local_d0,"%1.0f",(double)*param_6);
    break;
  case 0x31:
    lVar25 = FUN_14013a61c(0x301);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x301);
    }
    FUN_140003cf0(&local_d0,"%1.0f",(double)((uint)*param_6 * 0x100 + (uint)param_6[1]));
    FUN_140001834(local_b0,&DAT_1401b29b0);
    break;
  case 0x32:
    lVar25 = FUN_14013a61c(0x302);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x302);
    }
    dVar29 = (double)((*param_6 & 0x7f) * 0x100 + (uint)param_6[1]) * _DAT_1401c1708;
    if ((char)*param_6 < '\0') {
      dVar29 = dVar29 * DAT_1401c01f0;
    }
    FUN_140003cf0(&local_d0,"%1.0f",dVar29);
    FUN_140001834(local_b0,&DAT_1401b29c0);
    break;
  case 0x33:
    lVar25 = FUN_14013a61c(0x303);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x303);
    }
    FUN_140003cf0(&local_d0,"%1.0f",(double)*param_6);
    FUN_140001834(local_b0,"kPa abs");
    break;
  case 0x34:
    lVar25 = FUN_14013a61c(0x2db);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2db);
      lVar9 = local_e8;
    }
    FUN_140003cf0(&local_e0,lVar9,1,1);
    FUN_140003cf0(&local_d0,"%1.3f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * _DAT_1401c16f8);
    FUN_140001834(local_b0,"Lambda");
    FUN_140003cf0(local_c8,"%1.3f",
                  ((double)((uint)param_6[2] * 0x100 + (uint)param_6[3]) - _DAT_1401bdfc0) *
                  _DAT_1401bdfb0);
    FUN_140001834(local_a8,&DAT_1401b29c4);
    break;
  case 0x35:
    lVar25 = FUN_14013a61c(0x2db);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2db);
      lVar9 = local_e8;
    }
    FUN_140003cf0(&local_e0,lVar9,1,2);
    FUN_140003cf0(&local_d0,"%1.3f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * _DAT_1401c16f8);
    FUN_140001834(local_b0,"Lambda");
    FUN_140003cf0(local_c8,"%1.3f",
                  ((double)((uint)param_6[2] * 0x100 + (uint)param_6[3]) - _DAT_1401bdfc0) *
                  _DAT_1401bdfb0);
    FUN_140001834(local_a8,&DAT_1401b29c4);
    break;
  case 0x36:
    lVar25 = FUN_14013a61c(0x2db);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2db);
      lVar9 = local_e8;
    }
    if (param_7 == '\0') {
      FUN_140003cf0(&local_e0,lVar9,2,1);
    }
    else {
      FUN_140003cf0(&local_e0,lVar9,1,3);
    }
    FUN_140003cf0(&local_d0,"%1.3f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * _DAT_1401c16f8);
    FUN_140001834(local_b0,"Lambda");
    FUN_140003cf0(local_c8,"%1.3f",
                  ((double)((uint)param_6[2] * 0x100 + (uint)param_6[3]) - _DAT_1401bdfc0) *
                  _DAT_1401bdfb0);
    FUN_140001834(local_a8,&DAT_1401b29c4);
    break;
  case 0x37:
    lVar25 = FUN_14013a61c(0x2db);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2db);
      lVar9 = local_e8;
    }
    if (param_7 == '\0') {
      FUN_140003cf0(&local_e0,lVar9,2,2);
    }
    else {
      FUN_140003cf0(&local_e0,lVar9,1,4);
    }
    FUN_140003cf0(&local_d0,"%1.3f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * _DAT_1401c16f8);
    FUN_140001834(local_b0,"Lambda");
    FUN_140003cf0(local_c8,"%1.3f",
                  ((double)((uint)param_6[2] * 0x100 + (uint)param_6[3]) - _DAT_1401bdfc0) *
                  _DAT_1401bdfb0);
    FUN_140001834(local_a8,&DAT_1401b29c4);
    break;
  case 0x38:
    lVar25 = FUN_14013a61c(0x2db);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2db);
      lVar9 = local_e8;
    }
    if (param_7 == '\0') {
      FUN_140003cf0(&local_e0,lVar9,3,1);
    }
    else {
      FUN_140003cf0(&local_e0,lVar9,2,1);
    }
    FUN_140003cf0(&local_d0,"%1.3f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * _DAT_1401c16f8);
    FUN_140001834(local_b0,"Lambda");
    FUN_140003cf0(local_c8,"%1.3f",
                  ((double)((uint)param_6[2] * 0x100 + (uint)param_6[3]) - _DAT_1401bdfc0) *
                  _DAT_1401bdfb0);
    FUN_140001834(local_a8,&DAT_1401b29c4);
    break;
  case 0x39:
    lVar25 = FUN_14013a61c(0x2db);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2db);
      lVar9 = local_e8;
    }
    if (param_7 == '\0') {
      FUN_140003cf0(&local_e0,lVar9,3);
    }
    else {
      FUN_140003cf0(&local_e0,lVar9,2,2);
    }
    FUN_140003cf0(&local_d0,"%1.3f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * _DAT_1401c16f8);
    FUN_140001834(local_b0,"Lambda");
    FUN_140003cf0(local_c8,"%1.3f",
                  ((double)((uint)param_6[2] * 0x100 + (uint)param_6[3]) - _DAT_1401bdfc0) *
                  _DAT_1401bdfb0);
    FUN_140001834(local_a8,&DAT_1401b29c4);
    break;
  case 0x3a:
    lVar25 = FUN_14013a61c(0x2db);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2db);
      lVar9 = local_e8;
    }
    if (param_7 == '\0') {
      FUN_140003cf0(&local_e0,lVar9,4,1);
    }
    else {
      FUN_140003cf0(&local_e0,lVar9,2);
    }
    FUN_140003cf0(&local_d0,"%1.3f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * _DAT_1401c16f8);
    FUN_140001834(local_b0,"Lambda");
    FUN_140003cf0(local_c8,"%1.3f",
                  ((double)((uint)param_6[2] * 0x100 + (uint)param_6[3]) - _DAT_1401bdfc0) *
                  _DAT_1401bdfb0);
    FUN_140001834(local_a8,&DAT_1401b29c4);
    break;
  case 0x3b:
    lVar25 = FUN_14013a61c(0x2db);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2db);
      lVar9 = local_e8;
    }
    if (param_7 == '\0') {
      FUN_140003cf0(&local_e0,lVar9,4);
    }
    else {
      FUN_140003cf0(&local_e0,lVar9,2);
    }
    FUN_140003cf0(&local_d0,"%1.3f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * _DAT_1401c16f8);
    FUN_140001834(local_b0,"Lambda");
    FUN_140003cf0(local_c8,"%1.3f",
                  ((double)((uint)param_6[2] * 0x100 + (uint)param_6[3]) - _DAT_1401bdfc0) *
                  _DAT_1401bdfb0);
    FUN_140001834(local_a8,&DAT_1401b29c4);
    break;
  case 0x3c:
    lVar25 = FUN_14013a61c(0x304);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x304);
      lVar9 = local_e8;
    }
    FUN_140003cf0(&local_e0,lVar9,1,1);
    FUN_140003cf0(&local_d0,"%1.1f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * _DAT_1401bdfc8 -
                  DAT_1401c16e8);
    FUN_140001834(local_b0,&DAT_1401ab434);
    break;
  case 0x3d:
    lVar25 = FUN_14013a61c(0x304);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x304);
      lVar9 = local_e8;
    }
    FUN_140003cf0(&local_e0,lVar9,2,1);
    FUN_140003cf0(&local_d0,"%1.1f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * _DAT_1401bdfc8 -
                  DAT_1401c16e8);
    FUN_140001834(local_b0,&DAT_1401ab434);
    break;
  case 0x3e:
    lVar25 = FUN_14013a61c(0x304);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x304);
      lVar9 = local_e8;
    }
    FUN_140003cf0(&local_e0,lVar9,1,2);
    FUN_140003cf0(&local_d0,"%1.1f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * _DAT_1401bdfc8 -
                  DAT_1401c16e8);
    FUN_140001834(local_b0,&DAT_1401ab434);
    break;
  case 0x3f:
    lVar25 = FUN_14013a61c(0x304);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x304);
      lVar9 = local_e8;
    }
    FUN_140003cf0(&local_e0,lVar9,2,2);
    FUN_140003cf0(&local_d0,"%1.1f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * _DAT_1401bdfc8 -
                  DAT_1401c16e8);
    FUN_140001834(local_b0,&DAT_1401ab434);
    break;
  case 0x42:
    lVar25 = FUN_14013a61c(0x310);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x310);
    }
    FUN_140003cf0(&local_d0,"%1.3f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * DAT_1401bdfa8);
    FUN_140001834(local_b0,&DAT_1401ab500);
    break;
  case 0x43:
    lVar25 = FUN_14013a61c(0x311);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x311);
    }
    FUN_140003cf0(&local_d0,"%1.0f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * _DAT_1401c16e0);
    FUN_140001834(local_b0,&DAT_1401ab59c);
    break;
  case 0x44:
    lVar25 = FUN_14013a61c(0x312);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x312);
    }
    FUN_140003cf0(&local_d0,"%1.3f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * _DAT_1401c16d8);
    FUN_140001834(local_b0,"Lambda");
    break;
  case 0x45:
    lVar25 = FUN_14013a61c(0x313);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x313);
    }
    FUN_140001834(local_b0,&DAT_1401ab59c);
    FUN_140003cf0(&local_d0,"%1.1f",(double)*param_6 * _DAT_1401bdfe8 * DAT_1401bdf00);
    break;
  case 0x46:
    lVar25 = FUN_14013a61c(0x314);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x314);
    }
    FUN_140003cf0(&local_d0,"%1.0f",(double)(int)(*param_6 - 0x28));
    FUN_140001834(local_b0,&DAT_1401ab434);
    break;
  case 0x47:
    lVar25 = FUN_14013a61c(0x2d4);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2d4);
      lVar9 = local_e8;
    }
    FUN_140003cf0(&local_e0,lVar9,&DAT_1401b29c8);
    FUN_140001834(local_b0,&DAT_1401ab59c);
    FUN_140003cf0(&local_d0,"%1.0f",(double)*param_6 * _DAT_1401bdfe8 * DAT_1401bdf00);
    break;
  case 0x48:
    lVar25 = FUN_14013a61c(0x2d4);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x2d4);
      lVar9 = local_e8;
    }
    FUN_140003cf0(&local_e0,lVar9,&DAT_1401b29cc);
    FUN_140001834(local_b0,&DAT_1401ab59c);
    FUN_140003cf0(&local_d0,"%1.0f",(double)*param_6 * _DAT_1401bdfe8 * DAT_1401bdf00);
    break;
  case 0x49:
    lVar25 = FUN_14013a61c(0x315);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x315);
      lVar9 = local_e8;
    }
    FUN_140003cf0(&local_e0,lVar9,&DAT_1401b29d0);
    FUN_140001834(local_b0,&DAT_1401ab59c);
    FUN_140003cf0(&local_d0,"%1.1f",(double)*param_6 * _DAT_1401bdfe8 * DAT_1401bdf00);
    break;
  case 0x4a:
    lVar25 = FUN_14013a61c(0x315);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x315);
      lVar9 = local_e8;
    }
    FUN_140003cf0(&local_e0,lVar9,&DAT_1401b29d4);
    FUN_140001834(local_b0,&DAT_1401ab59c);
    FUN_140003cf0(&local_d0,"%1.1f",(double)*param_6 * _DAT_1401bdfe8 * DAT_1401bdf00);
    break;
  case 0x4b:
    lVar25 = FUN_14013a61c(0x315);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x315);
      lVar9 = local_e8;
    }
    FUN_140003cf0(&local_e0,lVar9,&DAT_1401b29d8);
    FUN_140001834(local_b0,&DAT_1401ab59c);
    FUN_140003cf0(&local_d0,"%1.0f",(double)*param_6 * _DAT_1401bdfe8 * DAT_1401bdf00);
    break;
  case 0x4c:
    lVar25 = FUN_14013a61c(0x31a);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x31a);
    }
    FUN_140001834(local_b0,&DAT_1401ab59c);
    FUN_140003cf0(&local_d0,"%1.1f",(double)*param_6 * _DAT_1401bdfe8 * DAT_1401bdf00);
    break;
  case 0x4d:
    lVar25 = FUN_14013a61c(0x31b);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x31b);
    }
    dVar28 = (double)((uint)*param_6 * 0x100 + (uint)param_6[1]);
    dVar29 = (double)FUN_14015ac50(dVar28 * _DAT_1401c16d0);
    FUN_140003cf0(&local_d0,"%1.0fh %1.0fmin",dVar29,dVar28 - dVar29 * _DAT_1401c16c8);
    break;
  case 0x4e:
    lVar25 = FUN_14013a61c(0x31c);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x31c);
    }
    dVar28 = (double)((uint)*param_6 * 0x100 + (uint)param_6[1]);
    dVar29 = (double)FUN_14015ac50(dVar28 * _DAT_1401c16d0);
    FUN_140003cf0(&local_d0,"%1.0fh,%1.0fmin",dVar29,dVar28 - dVar29 * _DAT_1401c16c8);
    break;
  case 0x4f:
    lVar25 = FUN_14013a61c(0x316);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x316);
    }
    FUN_140003cf0(&local_d0,"Max eq. ratio %d",*param_6);
    FUN_140003cf0(local_c8,"Max V O2s %d",param_6[1]);
    FUN_140001834(local_a8,&DAT_1401ab500);
    FUN_140003cf0(local_c0,"Max A O2s %d",param_6[2]);
    FUN_140001834(local_a0,&DAT_1401b29c4);
    FUN_140003cf0(local_b8,"Max MAP %d",((uint)param_6[3] + (uint)param_6[3] * 4) * 2);
    FUN_140001834(local_98,&DAT_1401b29b4);
    break;
  case 0x51:
    lVar25 = FUN_14013a61c(0x38a);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x38a);
    }
    lVar25 = FUN_14013a61c(0x39b);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x39b);
      lVar9 = local_e8;
    }
    bVar19 = *param_6;
    if (bVar19 < 9) {
      if (bVar19 == 8) {
        lVar25 = FUN_14013a61c(0x39a);
        if (lVar25 != 0) {
          FUN_1400018a8(&local_d0,lVar25,0x39a);
        }
      }
      else if (bVar19 == 1) {
        lVar25 = FUN_14013a61c(0x393);
        if (lVar25 != 0) {
          FUN_1400018a8(&local_d0,lVar25,0x393);
        }
      }
      else if (bVar19 == 2) {
        lVar25 = FUN_14013a61c(0x394);
        if (lVar25 != 0) {
          FUN_1400018a8(&local_d0,lVar25,0x394);
        }
      }
      else if (bVar19 == 3) {
        lVar25 = FUN_14013a61c(0x395);
        if (lVar25 != 0) {
          FUN_1400018a8(&local_d0,lVar25,0x395);
        }
      }
      else if (bVar19 == 4) {
        lVar25 = FUN_14013a61c(0x396);
        if (lVar25 != 0) {
          FUN_1400018a8(&local_d0,lVar25,0x396);
        }
      }
      else if (bVar19 == 5) {
        lVar25 = FUN_14013a61c(0x397);
        if (lVar25 != 0) {
          FUN_1400018a8(&local_d0,lVar25,0x397);
        }
      }
      else if (bVar19 == 6) {
        lVar25 = FUN_14013a61c(0x398);
        if (lVar25 != 0) {
          FUN_1400018a8(&local_d0,lVar25,0x398);
        }
      }
      else if ((bVar19 == 7) && (lVar25 = FUN_14013a61c(0x399), lVar25 != 0)) {
        FUN_1400018a8(&local_d0,lVar25,0x399);
      }
    }
    else if (bVar19 == 9) {
      lVar25 = FUN_14013a61c(0x393);
      if (lVar25 != 0) {
        FUN_1400018a8(&local_d8,lVar25,0x393);
        puVar15 = local_d8;
      }
      FUN_140003cf0(&local_d0,lVar9,puVar15);
    }
    else if (bVar19 == 10) {
      lVar25 = FUN_14013a61c(0x394);
      if (lVar25 != 0) {
        FUN_1400018a8(&local_d8,lVar25,0x394);
        puVar15 = local_d8;
      }
      FUN_140003cf0(&local_d0,lVar9,puVar15);
    }
    else if (bVar19 == 0xb) {
      lVar25 = FUN_14013a61c(0x395);
      if (lVar25 != 0) {
        FUN_1400018a8(&local_d8,lVar25,0x395);
        puVar15 = local_d8;
      }
      FUN_140003cf0(&local_d0,lVar9,puVar15);
    }
    else if (bVar19 == 0xc) {
      lVar25 = FUN_14013a61c(0x397);
      if (lVar25 != 0) {
        FUN_1400018a8(&local_d8,lVar25,0x397);
        puVar15 = local_d8;
      }
      FUN_140003cf0(&local_d0,lVar9,puVar15);
    }
    else if (bVar19 == 0xd) {
      lVar25 = FUN_14013a61c(0x398);
      if (lVar25 != 0) {
        FUN_1400018a8(&local_d8,lVar25,0x398);
        puVar15 = local_d8;
      }
      FUN_140003cf0(&local_d0,lVar9,puVar15);
    }
    else if (bVar19 == 0xe) {
      lVar25 = FUN_14013a61c(0x399);
      if (lVar25 != 0) {
        FUN_1400018a8(&local_d8,lVar25,0x399);
        puVar15 = local_d8;
      }
      FUN_140003cf0(&local_d0,lVar9,puVar15);
    }
    else if (bVar19 == 0xf) {
      lVar25 = FUN_14013a61c(0x39a);
      if (lVar25 != 0) {
        FUN_1400018a8(&local_d8,lVar25,0x39a);
        puVar15 = local_d8;
      }
      FUN_140003cf0(&local_d0,lVar9,puVar15);
    }
    break;
  case 0x52:
    lVar25 = FUN_14013a61c(0x38b);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x38b);
    }
    FUN_140003cf0(&local_d0,"%1.0f",(double)*param_6 * DAT_1401bdf00 * _DAT_1401bdfe8);
    FUN_140001834(local_b0,&DAT_1401ab59c);
    break;
  case 0x53:
    lVar25 = FUN_14013a61c(0x38c);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x38c);
    }
    FUN_140003cf0(&local_d0,"%1.0f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * _DAT_1401bdee8);
    FUN_140001834(local_b0,"kPa abs");
    break;
  case 0x54:
    lVar25 = FUN_14013a61c(0x38d);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x38d);
    }
    dVar29 = (double)((*param_6 & 0x7f) * 0x100 + (uint)param_6[1]);
    if ((char)*param_6 < '\0') {
      dVar29 = dVar29 * DAT_1401c01f0;
    }
    FUN_140003cf0(&local_d0,"%1.0f",dVar29);
    FUN_140001834(local_b0,&DAT_1401b29c0);
    break;
  case 0x55:
    lVar25 = FUN_14013a61c(0x38e);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x38e);
      lVar9 = local_e8;
    }
    lVar25 = FUN_14013a61c(0x392);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_d8,lVar25,0x392);
      puVar15 = local_d8;
    }
    FUN_140003cf0(&local_e0,lVar9,puVar15,1,CONCAT44(uVar33,3));
    FUN_140001834(local_b0,&DAT_1401ab59c);
    dVar28 = DAT_1401c1710;
    dVar29 = DAT_1401bdf00;
    FUN_140003cf0(&local_d0,"%1.1f",(double)(int)(*param_6 - 0x80) * DAT_1401bdf00 * DAT_1401c1710);
    if (param_6[1] != 0) {
      FUN_140001834(local_a8,&DAT_1401ab59c);
      FUN_140003cf0(local_c8,"%1.1f",(double)(int)(param_6[1] - 0x80) * dVar29 * dVar28);
    }
    break;
  case 0x56:
    lVar25 = FUN_14013a61c(0x38e);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x38e);
      lVar9 = local_e8;
    }
    lVar25 = FUN_14013a61c(0x391);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_d8,lVar25,0x391);
      puVar15 = local_d8;
    }
    FUN_140003cf0(&local_e0,lVar9,puVar15,1,CONCAT44(uVar33,3));
    FUN_140001834(local_b0,&DAT_1401ab59c);
    dVar28 = DAT_1401c1710;
    dVar29 = DAT_1401bdf00;
    FUN_140003cf0(&local_d0,"%1.1f",(double)(int)(*param_6 - 0x80) * DAT_1401bdf00 * DAT_1401c1710);
    if (param_6[1] != 0) {
      FUN_140001834(local_a8,&DAT_1401ab59c);
      FUN_140003cf0(local_c8,"%1.1f",(double)(int)(param_6[1] - 0x80) * dVar29 * dVar28);
    }
    break;
  case 0x57:
    lVar25 = FUN_14013a61c(0x38e);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x38e);
      lVar9 = local_e8;
    }
    lVar25 = FUN_14013a61c(0x392);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_d8,lVar25,0x392);
      puVar15 = local_d8;
    }
    FUN_140003cf0(&local_e0,lVar9,puVar15,2,CONCAT44(uVar33,4));
    FUN_140001834(local_b0,&DAT_1401ab59c);
    dVar28 = DAT_1401c1710;
    dVar29 = DAT_1401bdf00;
    FUN_140003cf0(&local_d0,"%1.1f",(double)(int)(*param_6 - 0x80) * DAT_1401bdf00 * DAT_1401c1710);
    if (param_6[1] != 0) {
      FUN_140001834(local_a8,&DAT_1401ab59c);
      FUN_140003cf0(local_c8,"%1.1f",(double)(int)(param_6[1] - 0x80) * dVar29 * dVar28);
    }
    break;
  case 0x58:
    lVar25 = FUN_14013a61c(0x38e);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x38e);
      lVar9 = local_e8;
    }
    lVar25 = FUN_14013a61c(0x391);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_d8,lVar25,0x391);
      puVar15 = local_d8;
    }
    FUN_140003cf0(&local_e0,lVar9,puVar15,2,CONCAT44(uVar33,4));
    FUN_140001834(local_b0,&DAT_1401ab59c);
    dVar28 = DAT_1401c1710;
    dVar29 = DAT_1401bdf00;
    FUN_140003cf0(&local_d0,"%1.1f",(double)(int)(*param_6 - 0x80) * DAT_1401bdf00 * DAT_1401c1710);
    if (param_6[1] != 0) {
      FUN_140001834(local_a8,&DAT_1401ab59c);
      FUN_140003cf0(local_c8,"%1.1f",(double)(int)(param_6[1] - 0x80) * dVar29 * dVar28);
    }
    break;
  case 0x59:
    lVar25 = FUN_14013a61c(0x38f);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x38f);
    }
    FUN_140003cf0(&local_d0,"%1.0f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * DAT_1401bd818);
    FUN_140001834(local_b0,"kPa abs");
    break;
  case 0x5a:
    lVar25 = FUN_14013a61c(0x390);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x390);
    }
    FUN_140001834(local_b0,&DAT_1401ab59c);
    FUN_140003cf0(&local_d0,"%1.0f",(double)*param_6 * _DAT_1401bdfe8 * DAT_1401bdf00);
    break;
  case 0x5b:
    lVar25 = FUN_14013a61c(0x433);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x433);
    }
    FUN_140001834(local_b0,&DAT_1401ab59c);
    FUN_140003cf0(&local_d0,"%1.1f",(double)*param_6 * _DAT_1401bdfe8 * DAT_1401bdf00);
    break;
  case 0x5c:
    lVar25 = FUN_14013a61c(0x434);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x434);
    }
    FUN_140003cf0(&local_d0,"%1.0f",(double)(int)(*param_6 - 0x28));
    FUN_140001834(local_b0,&DAT_1401ab434);
    break;
  case 0x5d:
    lVar25 = FUN_14013a61c(0x435);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x435);
    }
    FUN_140001834(local_b0,&DAT_1401ab438);
    FUN_140003cf0(&local_d0,"%1.2f",
                  ((double)((uint)*param_6 * 0x100 + (uint)param_6[1]) - _DAT_1401c16c0) *
                  DAT_1401c1710);
    break;
  case 0x5e:
    lVar25 = FUN_14013a61c(0x436);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x436);
    }
    FUN_140001834(local_b0,&DAT_1401b2a44);
    FUN_140003cf0(&local_d0,"%1.2f",
                  (double)((uint)*param_6 * 0x100 + (uint)param_6[1]) * _DAT_1401be048);
    break;
  case 0x5f:
    lVar25 = FUN_14013a61c(0x437);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x437);
    }
    bVar19 = *param_6;
    if (bVar19 == 0xe) {
      FUN_140001834(&local_d0,"HD Euro IV/B1");
    }
    else if (bVar19 == 0xf) {
      FUN_140001834(&local_d0,"HD Euro V/B2");
    }
    else if (bVar19 == 0x10) {
      FUN_140001834(&local_d0,"HD EURO EEC/C");
    }
    else {
      lVar25 = FUN_14013a61c(0x15e);
      if (lVar25 != 0) {
        FUN_1400018a8(&local_d0,lVar25,0x15e);
      }
    }
    FUN_140001834(local_b0,&DAT_14019aa80);
    break;
  case 0x61:
    lVar25 = FUN_14013a61c(0x438);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x438);
    }
    FUN_140001834(local_b0,&DAT_1401ab59c);
    FUN_140003cf0(&local_d0,"%1.1f",(double)*param_6 - _DAT_1401c16b8);
    break;
  case 0x62:
    lVar25 = FUN_14013a61c(0x439);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x439);
    }
    FUN_140001834(local_b0,&DAT_1401ab59c);
    FUN_140003cf0(&local_d0,"%1.1f",(double)*param_6 - _DAT_1401c16b8);
    break;
  case 99:
    lVar25 = FUN_14013a61c(0x43a);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x43a);
    }
    FUN_140001834(local_b0,&DAT_1401ab580);
    FUN_140003cf0(&local_d0,"%1.0f",(double)((uint)*param_6 * 0x100 + (uint)param_6[1]));
    break;
  case 100:
    lVar25 = FUN_14013a61c(0x43b);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x43b);
    }
    FUN_140001834(local_b0,&DAT_1401ab59c);
    dVar29 = DAT_1401bdfd8;
    FUN_140003cf0(&local_d0,"TQ_Max 1/2: %1.0f/%1.0f",(double)(int)(*param_6 - 0x7d) * DAT_1401bdfd8
                  ,(double)(int)(param_6[1] - 0x7d) * DAT_1401bdfd8);
    FUN_140001834(local_a8,&DAT_1401ab59c);
    FUN_140003cf0(&local_d0,"TQ_Max 3/4: %1.0f/%1.0f",(double)(int)(param_6[2] - 0x7d) * dVar29,
                  (double)(int)(param_6[3] - 0x7d) * dVar29);
    FUN_140001834(local_a0,&DAT_1401ab59c);
    FUN_140003cf0(&local_d0,"TQ_Max 5: %1.0f",(double)(int)(param_6[4] - 0x7d) * dVar29);
    break;
  case 0x65:
    lVar25 = FUN_14013a61c(0x43c);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x43c);
    }
    FUN_140001834(local_b0,&DAT_14019aa80);
    FUN_140001834(local_a8,&DAT_14019aa80);
    if ((*param_6 & 1) == 0) {
      FUN_140001834(&local_d0,&DAT_14019aa80);
    }
    else {
      FUN_140001834(&local_d0,"PTO_STAT:");
      if ((param_6[1] & 1) == 0) {
        FUN_14000ce1c(&local_d0,"OFF  ");
      }
      else {
        FUN_14000ce1c(&local_d0,&DAT_1401b2a94);
      }
    }
    if ((*param_6 & 2) != 0) {
      FUN_14000ce1c(&local_d0,"N/D_STAT:");
      if ((param_6[1] & 2) == 0) {
        FUN_14000ce1c(&local_d0,"DRIVE");
      }
      else {
        FUN_14000ce1c(&local_d0,"NEUTR");
      }
    }
    if ((*param_6 & 4) == 0) {
      FUN_140001834(local_c8,&DAT_14019aa80);
    }
    else {
      FUN_140001834(local_c8,"MT_GEAR:");
      if ((param_6[1] & 4) == 0) {
        FUN_14000ce1c(local_c8,"GEAR  ");
      }
      else {
        FUN_14000ce1c(local_c8,"NEUTR  ");
      }
    }
    if ((*param_6 & 8) != 0) {
      FUN_14000ce1c(local_c8,"GPL_STAT:");
      if ((param_6[1] & 8) == 0) {
        FUN_14000ce1c(local_c8,&DAT_1401b2af8);
      }
      else {
        FUN_14000ce1c(local_c8,&DAT_1401b2af4);
      }
    }
    if ((*param_6 & 0x10) != 0) {
      FUN_140001834(&local_e8,"GEAR_RCMD: ");
      if (param_6[1] >> 4 == 0) {
        FUN_14000ce1c(&local_e8,&DAT_1401ab4c8);
      }
      else if (param_6[1] >> 4 == 1) {
        FUN_14000ce1c(&local_e8,&DAT_1401ab4b0);
      }
      else {
        FUN_140003cf0(&local_d8,&DAT_14019b2e8);
        FUN_14000d230(&local_e8,local_d8,*(undefined4 *)(local_d8 + -2));
      }
      FUN_14000d230(&local_d0,local_e8,*(undefined4 *)(local_e8 + -0x10));
    }
    break;
  case 0x66:
    lVar25 = FUN_14013a61c(0x43d);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x43d);
    }
    FUN_140001834(&local_d0,"MAF A:");
    FUN_140001834(local_c8,"MAF B:");
    iVar17 = 0;
    lVar25 = 0;
    pbVar23 = param_6;
    do {
      iVar17 = iVar17 + 1;
      if ((*param_6 & (byte)iVar17) == 0) {
        FUN_140001834((longlong)&local_d0 + lVar25,&DAT_14019aa80);
        FUN_140001834(local_b0 + lVar25,&DAT_14019aa80);
      }
      else {
        FUN_140003cf0(&local_e8,"%1.2f",
                      ((double)((uint)pbVar23[1] << 8) + (double)pbVar23[2]) * _DAT_1401c16b0);
        FUN_14000d230((longlong)&local_d0 + lVar25,local_e8,*(undefined4 *)(local_e8 + -0x10));
        FUN_140001834(local_b0 + lVar25,&DAT_1401ab574);
      }
      lVar25 = lVar25 + 8;
      pbVar23 = pbVar23 + 2;
    } while (iVar17 < 2);
    break;
  case 0x67:
    lVar25 = FUN_14013a61c(0x43e);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x43e);
    }
    iVar17 = 1;
    lVar25 = 0;
    lVar9 = 2;
    pbVar23 = param_6;
    do {
      pbVar23 = pbVar23 + 1;
      lVar26 = (longlong)&local_d0 + lVar25;
      FUN_140003cf0(lVar26,"ECT %d:",iVar17);
      if ((*param_6 & (byte)iVar17) == 0) {
        FUN_140001834(lVar26,&DAT_14019aa80);
        FUN_140001834(local_b0 + lVar25,&DAT_14019aa80);
      }
      else {
        FUN_140003cf0(&local_e8,"%1.0f",(double)*pbVar23 - DAT_1401c16e8);
        FUN_14000d230(lVar26,local_e8,*(undefined4 *)(local_e8 + -0x10));
        FUN_140001834(local_b0 + lVar25,&DAT_1401ab434);
      }
      iVar17 = iVar17 + 1;
      lVar25 = lVar25 + 8;
      lVar9 = lVar9 + -1;
    } while (lVar9 != 0);
    break;
  case 0x68:
    lVar25 = FUN_14013a61c(0x43f);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x43f);
    }
    pbVar23 = param_6 + 4;
    lVar25 = 0;
    puVar30 = local_70;
    iVar17 = 1;
    do {
      bVar19 = *param_6;
      uVar13 = 1 << ((char)iVar17 - 1U & 0x1f) & (uint)bVar19;
      if ((uVar13 != 0) || ((bVar19 >> (iVar17 + 2U & 0x1f) & 1) != 0)) {
        if ((uVar13 == 0) || ((bVar19 >> (iVar17 + 2U & 0x1f) & 1) == 0)) {
          if (uVar13 == 0) {
            if ((param_6[1] & (byte)(1 << ((char)iVar17 + 2U & 0x1f))) != 0) {
              FUN_140003cf0((longlong)&local_d0 + lVar25,"IAT 2%d:",iVar17);
              puVar30 = (undefined1 *)(double)*pbVar23;
            }
          }
          else {
            FUN_140003cf0((longlong)&local_d0 + lVar25,"IAT 1%d:",iVar17);
            puVar30 = (undefined1 *)(double)pbVar23[-3];
          }
        }
        else {
          lVar9 = (longlong)&local_d0 + lVar25;
          FUN_140003cf0(lVar9,"IAT 1%d/2%d:",iVar17,iVar17);
          FUN_140003cf0(&local_e8,"%1.0f",(double)pbVar23[-3] - DAT_1401c16e8);
          FUN_14000d230(lVar9,local_e8,*(undefined4 *)(local_e8 + -0x10));
          FUN_14000ce1c(lVar9,&DAT_14019c864);
          puVar30 = (undefined1 *)(double)*pbVar23;
        }
        puVar30 = (undefined1 *)((double)puVar30 + _DAT_1401c16a8);
        FUN_140003cf0(&local_e8,"%1.0f",puVar30);
        FUN_14000d230((longlong)&local_d0 + lVar25,local_e8,*(undefined4 *)(local_e8 + -0x10));
        FUN_140001834(local_b0 + lVar25,&DAT_1401ab434);
      }
      pbVar23 = pbVar23 + 1;
      lVar25 = lVar25 + 8;
      bVar2 = iVar17 < 3;
      iVar17 = iVar17 + 1;
    } while (bVar2);
    break;
  case 0x69:
    lVar25 = FUN_14013a61c(0x440);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x440);
    }
    dVar29 = DAT_1401bdf00;
    local_88 = 0;
    iVar17 = 0;
    local_90 = (ulonglong)local_90._4_4_ << 0x20;
    lVar9 = 0;
    lVar25 = 0;
    do {
      FUN_140003cf0((longlong)&local_d0 + lVar25,"EGR %c:",(int)(char)((char)local_88 + 'A'));
      lVar26 = 0;
      do {
        if ((*param_6 & (byte)(1 << ((byte)iVar17 & 0x1f))) == 0) {
          FUN_140001834(&local_d8,&DAT_14019b574);
        }
        else {
          if (lVar26 == 2) {
            dVar28 = ((double)param_6[lVar26 + lVar9 + 1] - _DAT_1401be038) * dVar29 * DAT_1401c1710
            ;
          }
          else {
            dVar28 = (double)param_6[lVar26 + lVar9 + 1] * dVar29 * _DAT_1401bdfe8;
          }
          FUN_140003cf0(&local_d8,"%1.0f",dVar28);
        }
        if (lVar26 < 2) {
          FUN_14000ce1c(&local_d8,&DAT_14019c864);
        }
        FUN_14000d230((longlong)&local_d0 + lVar25,local_d8,*(undefined4 *)(local_d8 + -2));
        FUN_140001834(local_b0 + lVar25,&DAT_1401ab59c);
        iVar17 = iVar17 + 1;
        lVar26 = lVar26 + 1;
      } while (lVar26 < 3);
      local_88 = local_88 + 1;
      lVar9 = lVar9 + 3;
      lVar25 = lVar25 + 8;
      iVar17 = (int)local_90 + 3;
      local_90 = CONCAT44(local_90._4_4_,iVar17);
    } while (iVar17 < 6);
    break;
  case 0x6a:
    lVar25 = FUN_14013a61c(0x441);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x441);
    }
    iVar17 = 0;
    local_90 = (ulonglong)local_90._4_4_ << 0x20;
    lVar25 = 0;
    local_78 = 0;
    do {
      lVar26 = local_78;
      local_80 = (longlong)&local_d0 + local_78;
      FUN_140003cf0(local_80,"IAF_%c cmd/rel:",(int)(char)((char)iVar17 + 'A'));
      lVar9 = local_80;
      lVar16 = 0;
      iVar17 = iVar17 * 2;
      do {
        if ((*param_6 & (byte)(1 << ((byte)iVar17 & 0x1f))) == 0) {
          FUN_140001834(&local_d8,&DAT_14019b574);
        }
        else {
          FUN_140003cf0(&local_d8,"%1.0f",
                        (double)param_6[lVar25 + lVar16 + 1] * DAT_1401bdf00 * _DAT_1401bdfe8);
        }
        if (lVar16 < 1) {
          FUN_14000ce1c(&local_d8,&DAT_14019c864);
        }
        FUN_14000d230(lVar9,local_d8,*(undefined4 *)(local_d8 + -2));
        FUN_140001834(local_b0 + lVar26,&DAT_1401ab59c);
        iVar17 = iVar17 + 1;
        lVar16 = lVar16 + 1;
      } while (lVar16 < 2);
      iVar17 = (int)local_90 + 1;
      local_90 = CONCAT44(local_90._4_4_,iVar17);
      lVar25 = lVar25 + 2;
      local_78 = local_78 + 8;
    } while (iVar17 < 2);
    break;
  case 0x6b:
    lVar25 = FUN_14013a61c(0x442);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x442);
    }
    dVar29 = DAT_1401c16e8;
    uVar13 = 0;
    iVar17 = 1;
    local_90 = CONCAT44(local_90._4_4_,1);
    lVar25 = 0;
    local_78 = 0;
    local_80 = 2;
    do {
      lVar9 = local_78;
      local_68 = (longlong)&local_d0 + local_78;
      FUN_140003cf0(local_68,"EGRTemp %d1/%d2:",iVar17,iVar17);
      local_88 = 0;
      lVar26 = 0;
      local_70 = local_b0 + lVar9;
      iVar17 = 0;
      uVar22 = uVar13;
      do {
        if ((*param_6 >> (uVar22 & 0x1f) & 1) == 0) {
          if ((*param_6 >> (uVar13 + 4 + iVar17 & 0x1f) & 1) == 0) {
            FUN_140001834(&local_d8,&DAT_14019b574);
          }
          else {
            FUN_140003cf0(&local_d8,"%1.0f",
                          (double)param_6[lVar26 + lVar25 + 1] * _DAT_1401c16a0 - dVar29);
          }
        }
        else {
          FUN_140003cf0(&local_d8,"%1.0f",(double)param_6[lVar26 + lVar25 + 1] - dVar29);
        }
        if (lVar26 < 1) {
          FUN_14000ce1c(&local_d8,&DAT_14019c864);
        }
        FUN_14000d230(local_68,local_d8,*(undefined4 *)(local_d8 + -2));
        FUN_140001834(local_70,&DAT_1401ab434);
        iVar17 = iVar17 + 1;
        uVar22 = uVar22 + 1;
        lVar26 = lVar26 + 1;
      } while (lVar26 < 2);
      iVar17 = (int)local_90 + 1;
      local_90 = CONCAT44(local_90._4_4_,iVar17);
      lVar25 = lVar25 + 2;
      local_78 = local_78 + 8;
      uVar13 = uVar13 + 2;
      local_80 = local_80 + -1;
    } while (local_80 != 0);
    local_80 = 0;
    break;
  case 0x6c:
    lVar25 = FUN_14013a61c(0x443);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x443);
    }
    iVar17 = 0;
    local_90 = (ulonglong)local_90._4_4_ << 0x20;
    lVar25 = 0;
    local_80 = 0;
    do {
      lVar9 = local_80;
      local_70 = (undefined1 *)((longlong)&local_d0 + local_80);
      FUN_140003cf0(local_70,"THR %c cmd/rel:",(int)(char)((char)iVar17 + 'A'));
      puVar30 = local_70;
      lVar26 = 0;
      iVar17 = iVar17 * 2;
      do {
        if ((*param_6 & (byte)(1 << ((byte)iVar17 & 0x1f))) == 0) {
          FUN_140001834(&local_d8,&DAT_14019b574);
        }
        else {
          FUN_140003cf0(&local_d8,"%1.0f",
                        (double)param_6[lVar25 + lVar26 + 1] * DAT_1401bdf00 * _DAT_1401bdfe8);
        }
        if (lVar26 < 1) {
          FUN_14000ce1c(&local_d8,&DAT_14019c864);
        }
        FUN_14000d230(puVar30,local_d8,*(undefined4 *)(local_d8 + -2));
        FUN_140001834(local_b0 + lVar9,&DAT_1401ab59c);
        iVar17 = iVar17 + 1;
        lVar26 = lVar26 + 1;
      } while (lVar26 < 2);
      iVar17 = (int)local_90 + 1;
      local_90 = CONCAT44(local_90._4_4_,iVar17);
      lVar25 = lVar25 + 2;
      local_80 = local_80 + 8;
    } while (iVar17 < 2);
    break;
  case 0x6d:
    lVar25 = FUN_14013a61c(0x444);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x444);
    }
    iVar17 = 0;
    do {
      if (iVar17 == 0) {
        FUN_140001834(&local_d0,"FRP CMD:");
        FUN_140001834(local_b0,&DAT_1401b29b4);
      }
      else if (iVar17 == 1) {
        FUN_140001834(local_c8,"FRP ACT:");
        FUN_140001834(local_a8,&DAT_1401b29b4);
      }
      else if (iVar17 == 2) {
        FUN_140001834(local_c0,"FRTemp:");
        FUN_140001834(local_a0,&DAT_1401ab434);
      }
      iVar27 = 0;
      lVar25 = (longlong)iVar17;
      pbVar23 = param_6 + lVar25 + 1;
      iVar7 = iVar17;
      do {
        if ((*param_6 & (byte)(1 << ((byte)iVar7 & 0x1f))) == 0) {
          FUN_140001834(&local_d8,&DAT_14019b574);
        }
        else if (iVar17 < 2) {
          FUN_140003cf0(&local_d8,"%1.0f",
                        ((double)((uint)param_6[lVar25 * 2 + 1] << 8) +
                        (double)param_6[lVar25 * 2 + 2]) * DAT_1401bd818);
        }
        else {
          FUN_140003cf0(&local_d8,"%1.0f",(double)*pbVar23 - DAT_1401c16e8);
        }
        if (iVar27 < 1) {
          FUN_14000ce1c(&local_d8,&DAT_14019c864);
        }
        FUN_14000d230(&local_d0 + lVar25,local_d8,*(undefined4 *)(local_d8 + -2));
        iVar27 = iVar27 + 1;
        iVar7 = iVar7 + 3;
        pbVar23 = pbVar23 + 3;
      } while (iVar27 < 2);
      iVar17 = iVar17 + 1;
    } while (iVar17 < 3);
    break;
  case 0x6e:
    lVar25 = FUN_14013a61c(0x445);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x445);
    }
    iVar17 = 0;
    local_90 = (ulonglong)local_90._4_4_ << 0x20;
    lVar25 = 0;
    local_80 = 0;
    do {
      lVar9 = local_80;
      local_70 = (undefined1 *)((longlong)&local_d0 + local_80);
      FUN_140003cf0(local_70,"ICP_%c cmd/rel:",(int)(char)((char)iVar17 + 'A'));
      puVar30 = local_70;
      lVar26 = 0;
      iVar17 = iVar17 * 2;
      do {
        if ((*param_6 & (byte)(1 << ((byte)iVar17 & 0x1f))) == 0) {
          FUN_140001834(&local_d8,&DAT_14019b574);
        }
        else {
          FUN_140003cf0(&local_d8,"%1.0f",
                        (double)((uint)param_6[(lVar25 + lVar26) * 2 + 1] * 0x100 +
                                (uint)param_6[(lVar25 + lVar26) * 2 + 2]) * DAT_1401bd818);
        }
        if (lVar26 < 1) {
          FUN_14000ce1c(&local_d8,&DAT_14019c864);
        }
        FUN_14000d230(puVar30,local_d8,*(undefined4 *)(local_d8 + -2));
        FUN_140001834(local_b0 + lVar9,&DAT_1401b29c0);
        iVar17 = iVar17 + 1;
        lVar26 = lVar26 + 1;
      } while (lVar26 < 2);
      iVar17 = (int)local_90 + 1;
      local_90 = CONCAT44(local_90._4_4_,iVar17);
      lVar25 = lVar25 + 2;
      local_80 = local_80 + 8;
    } while (iVar17 < 2);
    break;
  case 0x6f:
    lVar25 = FUN_14013a61c(0x446);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x446);
    }
    iVar17 = 1;
    lVar25 = 0;
    lVar9 = 2;
    pbVar23 = param_6;
    do {
      pbVar23 = pbVar23 + 1;
      lVar26 = (longlong)&local_d0 + lVar25;
      FUN_140003cf0(lVar26,"TC%c_PRESS: ",iVar17 + 0x40);
      if ((*param_6 & (byte)iVar17) == 0) {
        FUN_140001834(lVar26,&DAT_14019aa80);
        FUN_140001834(local_b0 + lVar25,&DAT_14019aa80);
      }
      else {
        FUN_140003cf0(&local_e8,"%1.0f",(double)*pbVar23);
        FUN_14000d230(lVar26,local_e8,*(undefined4 *)(local_e8 + -0x10));
        FUN_140001834(local_b0 + lVar25,"kPa abs");
      }
      iVar17 = iVar17 + 1;
      lVar25 = lVar25 + 8;
      lVar9 = lVar9 + -1;
    } while (lVar9 != 0);
    break;
  case 0x70:
    lVar25 = FUN_14013a61c(0x447);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x447);
    }
    iVar17 = 0;
    local_90 = (ulonglong)local_90._4_4_ << 0x20;
    local_88 = 0;
    lVar25 = 0;
    local_80 = 0;
    iVar7 = 2;
    do {
      lVar9 = local_80;
      iVar27 = local_88;
      puVar30 = local_b0 + local_80;
      FUN_140001834(puVar30,&DAT_1401b29b4);
      lVar9 = (longlong)&local_d0 + lVar9;
      FUN_140003cf0(lVar9,"BP_%c ",(int)(char)((char)iVar17 + 'A'));
      uVar13 = 1 << ((byte)iVar27 + 1 & 0x1f) & (uint)*param_6;
      if (('\x01' << ((byte)iVar27 & 0x1f) & *param_6) == 0) {
        if (uVar13 == 0) {
          FUN_140001834(lVar9,&DAT_14019aa80);
          FUN_140001834(puVar30,&DAT_14019aa80);
        }
        else {
          FUN_14000ce1c(lVar9,&DAT_1401b2bfc);
        }
      }
      else if (uVar13 == 0) {
        FUN_14000ce1c(lVar9,&DAT_1401b2bf4);
      }
      else {
        FUN_14000ce1c(lVar9,"cmd/act:");
      }
      lVar26 = 0;
      do {
        if ((*param_6 & (byte)(1 << ((byte)iVar27 & 0x1f))) == 0) {
          FUN_140001834(&local_d8,&DAT_14019aa80);
        }
        else {
          FUN_140003cf0(&local_d8,"%1.1f",
                        (double)((uint)param_6[(lVar26 + lVar25) * 2 + 1] * 0x100 +
                                (uint)param_6[(lVar26 + lVar25) * 2 + 2]) * _DAT_1401c16b0);
          if ((lVar26 < 1) && (uVar13 != 0)) {
            FUN_14000ce1c(&local_d8,&DAT_14019c864);
          }
        }
        puVar15 = local_d8;
        FUN_14000d230(lVar9,local_d8,*(undefined4 *)(local_d8 + -2));
        iVar27 = iVar27 + 1;
        lVar26 = lVar26 + 1;
      } while (lVar26 < 2);
      iVar17 = (int)local_90 + 1;
      local_90 = CONCAT44(local_90._4_4_,iVar17);
      lVar25 = lVar25 + 2;
      local_80 = local_80 + 8;
      local_88 = local_88 + 3;
    } while (local_88 < 6);
    cVar24 = '\0';
    bVar19 = 0;
    lVar25 = 0;
    do {
      FUN_140001834(local_a0 + lVar25,&DAT_14019aa80);
      if ((*param_6 & (byte)(1 << ((byte)iVar7 & 0x1f))) != 0) {
        FUN_140003cf0(local_c0 + lVar25,"BP_%c:",(int)(char)(cVar24 + 'A'));
        bVar21 = param_6[9] >> (bVar19 & 0x1f);
        bVar20 = bVar21 & 3;
        if ((bVar21 & 3) == 0) {
          lVar9 = FUN_14013a61c(0x15e);
          if (lVar9 != 0) {
            FUN_1400018a8(&local_d8,lVar9,0x15e);
            puVar15 = local_d8;
          }
        }
        else if (bVar20 == 1) {
          lVar9 = FUN_14013a61c(0xda);
          if (lVar9 != 0) {
            FUN_1400018a8(&local_d8,lVar9,0xda);
            puVar15 = local_d8;
          }
        }
        else if (bVar20 == 2) {
          lVar9 = FUN_14013a61c(0xe3);
          if (lVar9 != 0) {
            FUN_1400018a8(&local_d8,lVar9,0xe3);
            puVar15 = local_d8;
          }
        }
        else if ((bVar20 == 3) && (lVar9 = FUN_14013a61c(0xe4), lVar9 != 0)) {
          FUN_1400018a8(&local_d8,lVar9,0xe4);
          puVar15 = local_d8;
        }
        FUN_14000d230(local_c0 + lVar25,puVar15,*(undefined4 *)(puVar15 + -2));
      }
      cVar24 = cVar24 + '\x01';
      iVar7 = iVar7 + 3;
      bVar19 = bVar19 + 2;
      lVar25 = lVar25 + 8;
    } while (iVar7 < 8);
    break;
  case 0x71:
    lVar25 = FUN_14013a61c(0x448);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x448);
    }
    iVar7 = 0;
    local_90 = (ulonglong)local_90._4_4_ << 0x20;
    local_88 = 0;
    lVar9 = 0;
    lVar25 = 0;
    iVar17 = 2;
    do {
      iVar27 = local_88;
      FUN_140003cf0((longlong)&local_d0 + lVar25,"VGT_%c cmd/act:",(int)(char)((char)iVar7 + 'A'));
      lVar26 = 0;
      do {
        if ((*param_6 & (byte)(1 << ((byte)iVar27 & 0x1f))) == 0) {
          FUN_140001834(&local_d8,&DAT_14019b574);
        }
        else {
          FUN_140003cf0(&local_d8,"%1.1f",
                        (double)param_6[lVar9 + lVar26 + 1] * DAT_1401bdf00 * _DAT_1401bdfe8);
        }
        if (lVar26 < 1) {
          FUN_14000ce1c(&local_d8,&DAT_14019c864);
        }
        puVar15 = local_d8;
        FUN_14000d230((longlong)&local_d0 + lVar25,local_d8,*(undefined4 *)(local_d8 + -2));
        FUN_140001834(local_b0 + lVar25,&DAT_1401ab59c);
        iVar27 = iVar27 + 1;
        lVar26 = lVar26 + 1;
      } while (lVar26 < 2);
      iVar7 = (int)local_90 + 1;
      local_90 = CONCAT44(local_90._4_4_,iVar7);
      lVar9 = lVar9 + 2;
      lVar25 = lVar25 + 8;
      local_88 = local_88 + 3;
    } while (local_88 < 6);
    cVar24 = '\0';
    bVar19 = 0;
    lVar25 = 0;
    do {
      FUN_140001834(local_a0 + lVar25,&DAT_14019aa80);
      if ((*param_6 & (byte)(1 << ((byte)iVar17 & 0x1f))) != 0) {
        FUN_140003cf0(local_c0 + lVar25,"VGT_%c:",(int)(char)(cVar24 + 'A'));
        bVar21 = param_6[5] >> (bVar19 & 0x1f);
        bVar20 = bVar21 & 3;
        if ((bVar21 & 3) == 0) {
          lVar9 = FUN_14013a61c(0x15e);
          if (lVar9 != 0) {
            FUN_1400018a8(&local_d8,lVar9,0x15e);
            puVar15 = local_d8;
          }
        }
        else if (bVar20 == 1) {
          lVar9 = FUN_14013a61c(0xda);
          if (lVar9 != 0) {
            FUN_1400018a8(&local_d8,lVar9,0xda);
            puVar15 = local_d8;
          }
        }
        else if (bVar20 == 2) {
          lVar9 = FUN_14013a61c(0xe3);
          if (lVar9 != 0) {
            FUN_1400018a8(&local_d8,lVar9,0xe3);
            puVar15 = local_d8;
          }
        }
        else if ((bVar20 == 3) && (lVar9 = FUN_14013a61c(0xe4), lVar9 != 0)) {
          FUN_1400018a8(&local_d8,lVar9,0xe4);
          puVar15 = local_d8;
        }
        FUN_14000d230(local_c0 + lVar25,puVar15,*(undefined4 *)(puVar15 + -2));
      }
      cVar24 = cVar24 + '\x01';
      iVar17 = iVar17 + 3;
      bVar19 = bVar19 + 2;
      lVar25 = lVar25 + 8;
    } while (iVar17 < 8);
    break;
  case 0x72:
    lVar25 = FUN_14013a61c(0x449);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x449);
    }
    iVar17 = 0;
    local_90 = (ulonglong)local_90._4_4_ << 0x20;
    local_88 = 0;
    lVar9 = 0;
    lVar25 = 0;
    do {
      iVar7 = local_88;
      FUN_140003cf0((longlong)&local_d0 + lVar25,"WG_%c cmd/act:",(int)(char)((char)iVar17 + 'A'));
      lVar26 = 0;
      do {
        if ((*param_6 & (byte)(1 << ((byte)iVar7 & 0x1f))) == 0) {
          FUN_140001834(&local_d8,&DAT_14019b574);
        }
        else {
          FUN_140003cf0(&local_d8,"%1.0f",
                        (double)param_6[lVar9 + lVar26 + 1] * DAT_1401bdf00 * _DAT_1401bdfe8);
        }
        if (lVar26 < 1) {
          FUN_14000ce1c(&local_d8,&DAT_14019c864);
        }
        FUN_14000d230((longlong)&local_d0 + lVar25,local_d8,*(undefined4 *)(local_d8 + -2));
        FUN_140001834(local_b0 + lVar25,&DAT_1401ab59c);
        iVar7 = iVar7 + 1;
        lVar26 = lVar26 + 1;
      } while (lVar26 < 2);
      iVar17 = (int)local_90 + 1;
      local_90 = CONCAT44(local_90._4_4_,iVar17);
      lVar9 = lVar9 + 2;
      lVar25 = lVar25 + 8;
      local_88 = local_88 + 3;
    } while (local_88 < 6);
    break;
  case 0x73:
    lVar25 = FUN_14013a61c(0x44a);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x44a);
    }
    iVar7 = 0;
    iVar17 = 0;
    lVar25 = 0;
    pbVar23 = param_6;
    do {
      if ((*param_6 & (byte)(1 << ((byte)iVar17 & 0x1f))) != 0) {
        FUN_140003cf0((longlong)&local_d0 + lVar25,"EP%d:",iVar7 + 1);
        FUN_140003cf0(&local_d8,"%1.2f",
                      (double)((uint)pbVar23[1] * 0x100 + (uint)pbVar23[2]) * DAT_1401bdfd8);
        FUN_140001834(local_b0 + lVar25,&DAT_1401b29b4);
        FUN_14000d230((longlong)&local_d0 + lVar25,local_d8,*(undefined4 *)(local_d8 + -2));
      }
      iVar7 = iVar7 + 1;
      iVar17 = iVar17 + 2;
      lVar25 = lVar25 + 8;
      pbVar23 = pbVar23 + 2;
    } while (iVar17 < 4);
    break;
  case 0x74:
    lVar25 = FUN_14013a61c(0x44a);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e0,lVar25,0x44a);
    }
    iVar7 = 0;
    iVar17 = 0;
    lVar25 = 0;
    pbVar23 = param_6;
    do {
      if ((*param_6 & (byte)(1 << ((byte)iVar17 & 0x1f))) != 0) {
        FUN_140003cf0((longlong)&local_d0 + lVar25,"TC%c_RPM:",iVar7 + 0x41);
        FUN_140003cf0(&local_d8,"%1.0f",(double)((uint)pbVar23[1] * 0x100 + (uint)pbVar23[2]));
        FUN_140001834(local_b0 + lVar25,&DAT_1401ab594);
        FUN_14000d230((longlong)&local_d0 + lVar25,local_d8,*(undefined4 *)(local_d8 + -2));
      }
      iVar7 = iVar7 + 1;
      iVar17 = iVar17 + 2;
      lVar25 = lVar25 + 8;
      pbVar23 = pbVar23 + 2;
    } while (iVar17 < 4);
    break;
  case 0x75:
  case 0x76:
    lVar25 = FUN_14013a61c(0x44c);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_e8,lVar25,0x44c);
      lVar9 = local_e8;
    }
    FUN_140003cf0(&local_e0,lVar9,param_5 + -0x34);
    lVar25 = FUN_14013a61c(0xe6);
    if (lVar25 != 0) {
      FUN_1400018a8(&local_d0,lVar25,0xe6);
    }
    dVar29 = DAT_1401c16e8;
    iVar17 = 0;
    pbVar23 = param_6;
    do {
      pbVar23 = pbVar23 + 1;
      if ((*param_6 & (byte)(1 << ((byte)iVar17 & 0x1f))) == 0) {
        FUN_140001834(&local_d8,&DAT_14019b574);
      }
      else {
        FUN_140003cf0(&local_d8,"%1.0f",(double)*pbVar23 - dVar29);
      }
      if (iVar17 < 1) {
        FUN_14000ce1c(&local_d8,&DAT_14019c864);
      }
      FUN_14000d230(&local_d0,local_d8,*(undefined4 *)(local_d8 + -2));
      FUN_140001834(local_b0,&DAT_1401ab434);
      iVar17 = iVar17 + 1;
    } while (iVar17 < 2);
    lVar25 = FUN_14013a61c(0xe6);
    if (lVar25 != 0) {
      FUN_1400018a8(local_c8,lVar25,0xe6);
    }
    iVar17 = 0;
    pbVar23 = param_6 + 4;
    do {
      if ((*param_6 & (byte)(1 << ((char)iVar17 + 2U & 0x1f))) == 0) {
        FUN_140001834(&local_d8,&DAT_14019b574);
      }
      else {
        FUN_140003cf0(&local_d8,"%1.0f",
                      (double)((uint)pbVar23[-1] * 0x100 + (uint)*pbVar23) * _DAT_1401bdfc8 - dVar29
                     );
      }
      if (iVar17 < 1) {
        FUN_14000ce1c(&local_d8,&DAT_14019c864);
      }
      FUN_14000d230(local_c8,local_d8,*(undefined4 *)(local_d8 + -2));
      FUN_140001834(local_a8,&DAT_1401ab434);
      iVar17 = iVar17 + 1;
      pbVar23 = pbVar23 + 2;
    } while (iVar17 < 2);
    break;
  case 0x77:
    FUN_140001870(&local_e0,0x44d);
    local_88 = 0;
    iVar17 = 1;
    local_90 = CONCAT44(local_90._4_4_,1);
    lVar25 = 0;
    local_78 = 0;
    local_80 = 2;
    do {
      iVar7 = local_88;
      bVar19 = '\x01' << ((byte)local_88 + 1 & 0x1f) & *param_6;
      if ((1 << ((byte)local_88 & 0x1f) & (uint)*param_6) == 0) {
        if (bVar19 != 0) {
          FUN_140003cf0((longlong)&local_d0 + lVar25,"B%dS2:",iVar17);
        }
      }
      else if (bVar19 == 0) {
        FUN_140003cf0((longlong)&local_d0 + lVar25,"B%dS1:",iVar17);
      }
      else {
        FUN_140003cf0((longlong)&local_d0 + lVar25,"B%dS1/B%dS2:",iVar17,iVar17);
      }
      lVar9 = local_78;
      lVar26 = 0;
      do {
        if ((*param_6 & (byte)(1 << ((byte)iVar7 & 0x1f))) == 0) {
          FUN_140001834(&local_d8,&DAT_14019aa80);
        }
        else {
          FUN_140003cf0(&local_d8,"%1.0f",(double)param_6[lVar26 + lVar9 + 1] - DAT_1401c16e8);
          if ((lVar26 == 0) && (bVar19 != 0)) {
            FUN_14000ce1c(&local_d8,&DAT_14019c864);
          }
          FUN_140001834(local_b0 + lVar25,&DAT_1401ab434);
        }
        FUN_14000d230((longlong)&local_d0 + lVar25,local_d8,*(undefined4 *)(local_d8 + -2));
        iVar7 = iVar7 + 1;
        lVar26 = lVar26 + 1;
      } while (lVar26 < 2);
      iVar17 = (int)local_90 + 1;
      local_90 = CONCAT44(local_90._4_4_,iVar17);
      local_78 = local_78 + 2;
      lVar25 = lVar25 + 8;
      local_88 = local_88 + 2;
      local_80 = local_80 + -1;
    } while (local_80 != 0);
    local_80 = 0;
    break;
  case 0x78:
  case 0x79:
    FUN_140001870(&local_e8,0x44e);
    FUN_140003cf0(&local_e0,local_e8,param_5 + -0x77,1,CONCAT44(uVar33,4));
    FUN_140001834(&local_d0,"S1/S2/S3/S4:");
    iVar17 = 0;
    pbVar23 = param_6;
    do {
      if ((*param_6 & (byte)(1 << ((byte)iVar17 & 0x1f))) == 0) {
        FUN_140001834(&local_d8,&DAT_14019b574);
      }
      else {
        FUN_140003cf0(&local_d8,"%1.0f",
                      (double)((uint)pbVar23[1] * 0x100 + (uint)pbVar23[2]) * _DAT_1401bdfc8 -
                      DAT_1401c16e8);
      }
      if (iVar17 < 3) {
        FUN_14000ce1c(&local_d8,&DAT_14019c864);
      }
      FUN_14000d230(local_c8,local_d8,*(undefined4 *)(local_d8 + -2));
      FUN_140001834(local_a8,&DAT_1401ab434);
      iVar17 = iVar17 + 1;
      pbVar23 = pbVar23 + 2;
    } while (iVar17 < 4);
    break;
  case 0x7a:
  case 0x7b:
    FUN_140001870(&local_e8,0x44f);
    FUN_140003cf0(&local_e0,local_e8,param_5 + -0x79);
    FUN_140001834(local_b0,&DAT_1401b29b4);
    dVar29 = DAT_1401bdfd8;
    if ((*param_6 & 1) == 0) {
      FUN_140001834(&local_d0,&DAT_14019b574);
    }
    else {
      dVar28 = (double)((param_6[1] & 0x7f) * 0x100 + (uint)param_6[2]) * DAT_1401bdfd8;
      if ((char)param_6[1] < '\0') {
        dVar28 = dVar28 * DAT_1401c01f0;
      }
      FUN_140003cf0(&local_d0,"%1.2f",dVar28);
    }
    FUN_14000ce1c(&local_d0,&DAT_14019c864);
    if ((*param_6 & 2) == 0) {
      FUN_140001834(&local_d8,&DAT_14019b574);
    }
    else {
      FUN_140003cf0(&local_d8,"%1.2f",(double)((uint)param_6[3] * 0x100 + (uint)param_6[4]) * dVar29
                   );
    }
    FUN_14000d230(&local_d0,local_d8,*(undefined4 *)(local_d8 + -2));
    FUN_14000ce1c(&local_d0,&DAT_14019c864);
    if ((*param_6 & 4) == 0) {
      FUN_140001834(&local_d8,&DAT_14019b574);
    }
    else {
      FUN_140003cf0(&local_d8,"%1.2f",(double)((uint)param_6[5] * 0x100 + (uint)param_6[6]) * dVar29
                   );
    }
    FUN_14000d230(&local_d0,local_d8,*(undefined4 *)(local_d8 + -2));
    break;
  case 0x7c:
    FUN_140001870(&local_e0,0x450);
    lVar9 = 0;
    lVar25 = 0;
    iVar17 = 0;
    do {
      local_90 = CONCAT44(local_90._4_4_,iVar17 + 1);
      FUN_140003cf0((longlong)&local_d0 + lVar25,&DAT_1401b2c80,iVar17 + 1);
      lVar26 = 0;
      iVar17 = iVar17 * 2;
      do {
        if ((*param_6 & (byte)(1 << ((byte)iVar17 & 0x1f))) == 0) {
          FUN_140001834(&local_d8,&DAT_14019b574);
        }
        else {
          FUN_140003cf0(&local_d8,"%1.0f",
                        (double)((uint)param_6[(lVar9 + lVar26) * 2 + 1] * 0x100 +
                                (uint)param_6[(lVar9 + lVar26) * 2 + 2]) * _DAT_1401bdfc8 -
                        DAT_1401c16e8);
        }
        if (lVar26 < 1) {
          FUN_14000ce1c(&local_d8,&DAT_14019c864);
        }
        FUN_14000d230((longlong)&local_d0 + lVar25,local_d8,*(undefined4 *)(local_d8 + -2));
        FUN_140001834(local_b0 + lVar25,&DAT_1401ab434);
        iVar17 = iVar17 + 1;
        lVar26 = lVar26 + 1;
      } while (lVar26 < 2);
      lVar9 = lVar9 + 2;
      lVar25 = lVar25 + 8;
      iVar17 = (int)local_90;
    } while ((int)local_90 < 2);
    break;
  case 0x7d:
  case 0x7e:
    if (param_5 == 0x7d) {
      FUN_140001870(&local_e0,0x451);
    }
    else {
      FUN_140001870(&local_e0,0x452);
    }
    if ((*param_6 & 1) != 0) {
      FUN_140001870(&local_d0,0xe7);
      FUN_14000ce1c(&local_d0,&DAT_14019b56c);
    }
    if ((*param_6 & 2) != 0) {
      FUN_140001870(&local_e8,0xe8);
      FUN_14000d230(&local_d0,local_e8,*(undefined4 *)(local_e8 + -0x10));
      FUN_14000ce1c(&local_d0,&DAT_14019b56c);
    }
    if ((*param_6 & 4) != 0) {
      FUN_140001870(&local_e8,0xe9);
      FUN_14000d230(&local_d0,local_e8,*(undefined4 *)(local_e8 + -0x10));
      FUN_14000ce1c(&local_d0,&DAT_14019b56c);
    }
    if ((*param_6 & 8) != 0) {
      FUN_140001870(&local_e8,0xea);
      FUN_14000d230(&local_d0,local_e8,*(undefined4 *)(local_e8 + -0x10));
      FUN_14000ce1c(&local_d0,&DAT_14019b56c);
    }
    break;
  case 0x7f:
    FUN_140001870(&local_e0,0x453);
    iVar7 = 0;
    iVar17 = 0;
    do {
      if ((*param_6 & (byte)(1 << ((byte)iVar17 & 0x1f))) != 0) {
        if (iVar17 == 0) {
          FUN_140001870(&local_d0 + iVar7);
        }
        else if (iVar17 == 1) {
          FUN_140001870(&local_d0 + iVar7);
        }
        else if (iVar17 == 2) {
          FUN_140001870(&local_d0 + iVar7);
        }
        uVar18 = 0;
        lVar25 = 0;
        bVar19 = 0x18;
        do {
          uVar18 = uVar18 + (longlong)
                            (int)((uint)param_6[lVar25 + (longlong)iVar17 * 4 + 1] <<
                                 (bVar19 & 0x1f));
          bVar19 = bVar19 - 8;
          lVar25 = lVar25 + 1;
        } while (lVar25 < 4);
        auVar3._8_8_ = 0;
        auVar3._0_8_ = uVar18;
        lVar25 = SUB168(ZEXT816(0x23456789abcdf013) * auVar3,8);
        dVar29 = (double)(longlong)uVar18;
        if ((longlong)uVar18 < 0) {
          dVar29 = dVar29 + _DAT_1401c1698;
        }
        dVar29 = (double)FUN_14015ac50(dVar29 * _DAT_1401bedf8);
        FUN_140003cf0(&local_e8,"%dH%02ds",(int)dVar29,
                      uVar18 + ((uVar18 - lVar25 >> 1) + lVar25 >> 0xb) * -0xe10);
        FUN_14000ce1c(&local_d0 + iVar7,&DAT_14019c55c);
        FUN_14000d230(&local_d0 + iVar7);
        iVar7 = iVar7 + 1;
      }
      iVar17 = iVar17 + 1;
    } while (iVar17 < 3);
    break;
  case 0x81:
  case 0x82:
  case 0x89:
  case 0x8a:
    FUN_140001870(&local_e8);
    if (param_5 == 0x81) {
      FUN_140003cf0(&local_e0,local_e8,1,4);
    }
    else if (param_5 == 0x82) {
      FUN_140003cf0(&local_e0);
    }
    else if (param_5 == 0x89) {
      FUN_140003cf0(&local_e0);
    }
    else if (param_5 == 0x8a) {
      FUN_140003cf0(&local_e0);
    }
    iVar17 = 0;
    puVar15 = &local_d0;
    lVar25 = 0;
    do {
      lVar9 = 0;
      iVar7 = 1 << ((byte)iVar17 & 0x1f);
      local_90 = CONCAT44(local_90._4_4_,iVar7);
      do {
        if ((*param_6 & (byte)iVar7) != 0) {
          uVar18 = 0;
          lVar26 = 0;
          bVar19 = 0x18;
          do {
            uVar18 = uVar18 + (longlong)
                              (int)((uint)param_6[lVar26 + (lVar9 + lVar25) * 4 + 1] <<
                                   (bVar19 & 0x1f));
            bVar19 = bVar19 - 8;
            lVar26 = lVar26 + 1;
          } while (lVar26 < 4);
          if (uVar18 == 0xffffffffffffffff) {
            FUN_140001834(&local_e8,&DAT_14019bfbc);
          }
          else {
            auVar4._8_8_ = 0;
            auVar4._0_8_ = uVar18;
            lVar26 = SUB168(ZEXT816(0x23456789abcdf013) * auVar4,8);
            local_70 = (undefined1 *)(uVar18 + ((uVar18 - lVar26 >> 1) + lVar26 >> 0xb) * -0xe10);
            dVar29 = (double)(longlong)uVar18;
            if ((longlong)uVar18 < 0) {
              dVar29 = dVar29 + _DAT_1401c1698;
            }
            dVar29 = (double)FUN_14015ac50(dVar29 * _DAT_1401bedf8);
            FUN_140003cf0(&local_e8,"%dH%02ds",(int)dVar29,local_70);
          }
          if (lVar9 == 0) {
            FUN_14000ce1c(&local_e8,&DAT_14019e2c0);
          }
          FUN_14000d230(puVar15);
          iVar7 = (int)local_90;
        }
        lVar9 = lVar9 + 1;
      } while (lVar9 < 2);
      iVar17 = iVar17 + 1;
      lVar25 = lVar25 + 2;
      puVar15 = puVar15 + 1;
    } while (iVar17 < 4);
    break;
  case 0x83:
    FUN_140001870(&local_e0,0x455);
    iVar17 = 1;
    lVar25 = 0;
    lVar9 = 2;
    pbVar23 = param_6;
    do {
      lVar26 = (longlong)&local_d0 + lVar25;
      FUN_140003cf0(lVar26,"NOx%d1:",iVar17);
      if ((*param_6 & (byte)iVar17) == 0) {
        FUN_140001834(&local_e8,&DAT_14019b574);
        FUN_14000d230(lVar26,local_e8,*(undefined4 *)(local_e8 + -0x10));
        FUN_140001834(local_b0 + lVar25,&DAT_14019aa80);
      }
      else {
        FUN_140003cf0(&local_e8,"%1.0f",(double)((uint)pbVar23[1] << 8) + (double)pbVar23[2]);
        FUN_14000d230(lVar26,local_e8,*(undefined4 *)(local_e8 + -0x10));
        FUN_140001834(local_b0 + lVar25,&DAT_1401b2ca0);
      }
      iVar17 = iVar17 + 1;
      lVar25 = lVar25 + 8;
      lVar9 = lVar9 + -1;
      pbVar23 = pbVar23 + 2;
    } while (lVar9 != 0);
    break;
  case 0x84:
    FUN_140001870(&local_e0,0x456);
    FUN_140003cf0(&local_d0,"%1.0f",(double)(int)(*param_6 - 0x28));
    FUN_140001834(local_b0,&DAT_1401ab434);
    break;
  case 0x85:
    FUN_140001870(&local_e0,0x457);
    FUN_140001870(&local_d0,0xef);
    dVar29 = DAT_1401bdfd0;
    if ((*param_6 & 1) == 0) {
      FUN_140001834(&local_e8,&DAT_14019b574);
    }
    else {
      FUN_140003cf0(&local_e8,"%1.1f",(double)param_6[2] * DAT_1401bdfd0);
    }
    FUN_14000d230(&local_d0,local_e8,*(undefined4 *)(local_e8 + -0x10));
    FUN_14000ce1c(&local_d0,&DAT_14019c864);
    if ((*param_6 & 2) == 0) {
      FUN_140001834(&local_e8,&DAT_14019b574);
    }
    else {
      FUN_140003cf0(&local_e8,"%1.2f",(double)param_6[4] * dVar29);
      FUN_140001834(local_b0,&DAT_1401b2a44);
    }
    FUN_14000d230(&local_d0,local_e8,*(undefined4 *)(local_e8 + -0x10));
    FUN_140001870(local_c8,0xf0);
    if ((*param_6 & 4) == 0) {
      FUN_140001834(&local_e8,&DAT_14019b574);
    }
    else {
      FUN_140003cf0(&local_e8,"%1.1f",(double)param_6[5] * DAT_1401bdf00 * _DAT_1401bdfe8);
      FUN_140001834(local_a8,&DAT_1401ab59c);
    }
    FUN_14000d230(local_c8);
    FUN_140001870(local_c0);
    if ((*param_6 & 8) == 0) {
      FUN_140001834(&local_e8,&DAT_14019b574);
    }
    else {
      uVar18 = 0;
      lVar25 = 0;
      bVar19 = 0x18;
      do {
        uVar18 = uVar18 + (longlong)(int)((uint)param_6[lVar25 + 6] << (bVar19 & 0x1f));
        bVar19 = bVar19 - 8;
        lVar25 = lVar25 + 1;
      } while (lVar25 < 4);
      auVar5._8_8_ = 0;
      auVar5._0_8_ = uVar18;
      lVar25 = SUB168(ZEXT816(0x23456789abcdf013) * auVar5,8);
      dVar29 = (double)(longlong)uVar18;
      if ((longlong)uVar18 < 0) {
        dVar29 = dVar29 + _DAT_1401c1698;
      }
      dVar29 = (double)FUN_14015ac50(dVar29 * _DAT_1401bedf8);
      FUN_140003cf0(&local_e8,"%dH%02ds",(int)dVar29,
                    uVar18 + ((uVar18 - lVar25 >> 1) + lVar25 >> 0xb) * -0xe10);
    }
    FUN_14000d230(local_c0,local_e8,*(undefined4 *)(local_e8 + -0x10));
    break;
  case 0x86:
    FUN_140001870(&local_e0,0x458);
    iVar17 = 1;
    lVar25 = 0;
    lVar9 = 2;
    pbVar23 = param_6;
    do {
      lVar26 = (longlong)&local_d0 + lVar25;
      FUN_140003cf0(lVar26,"PM%d1:",iVar17);
      if ((*param_6 & (byte)iVar17) == 0) {
        FUN_140001834(&local_e8,&DAT_14019b574);
        FUN_14000d230(lVar26,local_e8,*(undefined4 *)(local_e8 + -0x10));
        FUN_140001834(local_b0 + lVar25,&DAT_14019aa80);
      }
      else {
        FUN_140003cf0(&local_e8,"%1.2f",
                      ((double)((uint)pbVar23[1] << 8) + (double)pbVar23[2]) * _DAT_1401c1690);
        FUN_14000d230(lVar26,local_e8,*(undefined4 *)(local_e8 + -0x10));
        FUN_140001834(local_b0 + lVar25,"mg/m3");
      }
      iVar17 = iVar17 + 1;
      lVar25 = lVar25 + 8;
      lVar9 = lVar9 + -1;
      pbVar23 = pbVar23 + 2;
    } while (lVar9 != 0);
    break;
  case 0x87:
    FUN_140001870(&local_e0,0x459);
    iVar17 = 1;
    lVar25 = 0;
    lVar9 = 2;
    pbVar23 = param_6;
    do {
      lVar26 = (longlong)&local_d0 + lVar25;
      FUN_140003cf0(lVar26,"MAP_%c:",iVar17 + 0x40);
      if ((*param_6 & (byte)iVar17) == 0) {
        FUN_140001834(&local_e8,&DAT_14019b574);
        FUN_14000d230(lVar26,local_e8,*(undefined4 *)(local_e8 + -0x10));
        FUN_140001834(local_b0 + lVar25,&DAT_14019aa80);
      }
      else {
        FUN_140003cf0(&local_e8,"%1.2f",
                      ((double)((uint)pbVar23[1] << 8) + (double)pbVar23[2]) * _DAT_1401c16b0);
        FUN_14000d230(lVar26,local_e8,*(undefined4 *)(local_e8 + -0x10));
        FUN_140001834(local_b0 + lVar25,"kPa abs");
      }
      iVar17 = iVar17 + 1;
      lVar25 = lVar25 + 8;
      lVar9 = lVar9 + -1;
      pbVar23 = pbVar23 + 2;
    } while (lVar9 != 0);
    break;
  case 0x8b:
    FUN_140001870(&local_e0,0x317);
    if (((*param_6 & 1) != 0) || (uVar22 = 0, (*param_6 & 2) != 0)) {
      FUN_140001834(&local_d0,"PF_Regen:");
      if ((*param_6 & 1) != 0) {
        FUN_14000ce1c(&local_d0,"Status:");
        if ((param_6[1] & 1) == 0) {
          FUN_14000ce1c(&local_d0,&DAT_1401ab4c8);
        }
        else {
          FUN_14000ce1c(&local_d0,&DAT_1401b2cd8);
        }
        if ((*param_6 & 2) != 0) {
          FUN_14000ce1c(&local_d0,&DAT_14019c078);
        }
      }
      uVar22 = uVar13;
      if ((*param_6 & 2) != 0) {
        FUN_14000ce1c(&local_d0,&DAT_1401b2cdc);
        if ((param_6[1] & 2) == 0) {
          FUN_14000ce1c(&local_d0,"Pass.");
        }
        else {
          FUN_14000ce1c(&local_d0,&DAT_1401b2ce4);
        }
      }
    }
    if (((*param_6 & 4) != 0) || ((*param_6 & 8) != 0)) {
      puVar14 = &local_d0 + (int)uVar22;
      FUN_140001834(puVar14,&DAT_1401b2cf4);
      if ((*param_6 & 4) != 0) {
        FUN_14000ce1c(puVar14,"Regen:");
        if ((param_6[1] & 4) == 0) {
          FUN_14000ce1c(puVar14,&DAT_1401ab4c8);
        }
        else {
          FUN_14000ce1c(puVar14,&DAT_1401b2cd8);
        }
        if ((*param_6 & 8) != 0) {
          FUN_14000ce1c(puVar14,&DAT_14019c078);
        }
      }
      if ((*param_6 & 8) != 0) {
        if ((param_6[(int)uVar22] & 8) == 0) {
          FUN_14000ce1c(puVar14,"DSulf:N");
        }
        else {
          FUN_14000ce1c(puVar14,"DSulf:Y");
        }
      }
      uVar22 = uVar22 + 1;
    }
    if ((*param_6 & 0x10) != 0) {
      FUN_140003cf0(&local_d0 + (int)uVar22,"PF_Load: %1.1f",
                    (double)param_6[2] * DAT_1401bdf00 * _DAT_1401bdfe8);
      FUN_140001834(local_b0 + (longlong)(int)uVar22 * 8,&DAT_1401ab59c);
      uVar22 = uVar22 + 1;
    }
    if ((*param_6 & 0x20) != 0) {
      uVar18 = (longlong)(int)((uint)param_6[3] << 8) + (ulonglong)param_6[4];
      auVar6._8_8_ = 0;
      auVar6._0_8_ = uVar18;
      lVar25 = SUB168(ZEXT816(0x23456789abcdf013) * auVar6,8);
      dVar29 = (double)FUN_14015ac50((double)uVar18 * _DAT_1401bedf8);
      FUN_140003cf0(&local_e8,"%dH%02dm",(int)dVar29,
                    (uVar18 + ((uVar18 - lVar25 >> 1) + lVar25 >> 0xb) * -0xe10) / 0x3c);
      lVar9 = local_e8;
    }
    if ((*param_6 & 0x40) != 0) {
      FUN_140003cf0(&local_d8,&DAT_14019b2e8,
                    (longlong)(int)((uint)param_6[5] << 8) + (ulonglong)param_6[6]);
      puVar15 = local_d8;
    }
    if (((uVar22 < 3) || ((*param_6 & 0x20) == 0)) || ((*param_6 & 0x40) == 0)) {
      if ((*param_6 & 0x20) != 0) {
        FUN_140003cf0(&local_d0 + (int)uVar22,"PF_Regen_Avg_T: %s",lVar9);
        uVar22 = uVar22 + 1;
      }
      if ((*param_6 & 0x40) != 0) {
        FUN_140003cf0(&local_d0 + (int)uVar22,"PF_Regen_Avg_D: %s",puVar15);
        FUN_140001834(local_b0 + (longlong)(int)uVar22 * 8,&DAT_1401b29b0);
      }
    }
    else {
      FUN_140003cf0(&local_d0 + (int)uVar22,"PF_Rgn_Avg_T/D:%skm %s",lVar9,puVar15);
    }
    break;
  case 0x8c:
  case 0x9c:
    FUN_140001870(&local_e0,0x318);
    bVar19 = -(param_5 != 0x8c) & 2;
    iVar17 = 0;
    uVar13 = 4;
    do {
      lVar25 = 2;
      do {
        if ((*param_6 >> (uVar13 - 4 & 0x1f) & 1) != 0) {
          iVar17 = iVar17 + 1;
        }
        if ((*param_6 >> (uVar13 & 0x1f) & 1) != 0) {
          iVar17 = iVar17 + 1;
        }
        uVar13 = uVar13 + 1;
        lVar25 = lVar25 + -1;
      } while (lVar25 != 0);
    } while ((int)uVar13 < 8);
    if (iVar17 < 5) {
      iVar7 = (char)bVar19 + 1;
      local_90 = CONCAT44(local_90._4_4_,iVar7);
      lVar25 = 0;
      iVar17 = 0;
      do {
        lVar9 = 0;
        iVar27 = iVar17 + 1;
        iVar17 = iVar17 * 2;
        do {
          lVar26 = (longlong)iVar17;
          FUN_140003cf0(&local_d0 + lVar26,"B%dS%d:",iVar27,iVar7);
          if ((*param_6 & (byte)(1 << ((byte)iVar17 & 0x1f))) != 0) {
            FUN_140003cf0(&local_d8,"%1.6f",
                          ((double)((uint)param_6[(lVar9 + lVar25) * 2 + 1] << 8) +
                          (double)param_6[(lVar9 + lVar25) * 2 + 2]) * _DAT_1401c1688);
            FUN_140001834(local_b0 + lVar26 * 8,&DAT_1401ab59c);
          }
          if ((*param_6 & (byte)(1 << ((byte)iVar17 + 4 & 0x1f))) != 0) {
            FUN_140003cf0(&local_d8,"%1.3f",
                          ((double)((uint)param_6[(lVar9 + lVar25) * 2 + 9] << 8) +
                          (double)param_6[(lVar9 + lVar25) * 2 + 10]) * _DAT_1401c1680);
            FUN_140001834(local_b0 + lVar26 * 8,&DAT_1401b2d98);
          }
          FID_conflict_operator_(&local_d0 + lVar26,&local_d8);
          iVar7 = iVar7 + 1;
          iVar17 = iVar17 + 1;
          lVar9 = lVar9 + 1;
        } while (lVar9 < 2);
        lVar25 = lVar25 + 2;
        iVar7 = (int)local_90;
        iVar17 = iVar27;
      } while (iVar27 < 2);
    }
    else {
      iVar17 = (char)bVar19 + 1;
      local_90 = CONCAT44(local_90._4_4_,iVar17);
      lVar25 = 0;
      iVar7 = 0;
      do {
        lVar9 = 0;
        iVar27 = iVar7 + 1;
        uVar13 = iVar7 * 2;
        do {
          lVar26 = (longlong)(int)uVar13;
          FUN_140003cf0(&local_d0 + lVar26,"B%dS%d:",iVar27,iVar17);
          FUN_140001834(&local_d8,&DAT_14019aa80);
          if ((*param_6 >> (uVar13 & 0x1f) & 1) != 0) {
            dVar29 = ((double)((uint)param_6[(lVar25 + lVar9) * 2 + 1] << 8) +
                     (double)param_6[(lVar25 + lVar9) * 2 + 2]) * _DAT_1401c1688;
            if ((*param_6 >> (uVar13 + 4 & 0x1f) & 1) == 0) {
              FUN_140003cf0(&local_d8,"%1.6f",dVar29);
              FUN_140001834(local_b0 + lVar26 * 8,&DAT_1401ab59c);
            }
            else {
              FUN_140003cf0(&local_d8,"%1.3f/",dVar29);
              FUN_140001834(local_b0 + lVar26 * 8,"%/lbd");
            }
          }
          if ((*param_6 & (byte)(1 << ((char)uVar13 + 4U & 0x1f))) != 0) {
            FUN_140003cf0(&local_e8,"%1.3f",
                          ((double)((uint)param_6[(lVar25 + lVar9) * 2 + 9] << 8) +
                          (double)param_6[(lVar25 + lVar9) * 2 + 10]) * _DAT_1401c1680);
            if (*(int *)(local_d8 + -2) == 0) {
              FUN_140001834(local_b0 + lVar26 * 8,&DAT_1401b2d98);
            }
            FUN_14000d230(&local_d8,local_e8,*(undefined4 *)(local_e8 + -0x10));
          }
          FID_conflict_operator_(&local_d0 + lVar26,&local_d8);
          iVar17 = iVar17 + 1;
          uVar13 = uVar13 + 1;
          lVar9 = lVar9 + 1;
        } while (lVar9 < 2);
        lVar25 = lVar25 + 2;
        iVar17 = (int)local_90;
        iVar7 = iVar27;
      } while (iVar27 < 2);
    }
    break;
  case 0x8d:
    FUN_140001870(&local_e8,0x2d4);
    FUN_140003cf0(&local_e0,local_e8,&DAT_1401b2d9c);
    FUN_140001834(local_b0,&DAT_1401ab59c);
    FUN_140003cf0(&local_d0,"%1.0f",(double)*param_6 * _DAT_1401bdfe8 * DAT_1401bdf00);
    break;
  case 0x8e:
    FUN_140001870(&local_e0,0x319);
    FUN_140001834(local_b0,&DAT_1401ab59c);
    FUN_140003cf0(&local_d0,"%1.1f",(double)*param_6 - _DAT_1401c16b8);
    break;
  case 0x8f:
    FUN_140001870(&local_e0,0x5f5);
    bVar19 = 0;
    iVar17 = 1;
    pbVar23 = param_6 + 2;
    lVar25 = 0;
    local_90 = 2;
    do {
      if ((*param_6 & (byte)(1 << (bVar19 & 0x1f))) != 0) {
        lVar9 = (longlong)&local_d0 + lVar25;
        FUN_140003cf0(lVar9,"B%dS1:",iVar17);
        bVar21 = pbVar23[-1] & 3;
        if ((pbVar23[-1] & 3) == 0) {
          FUN_14000ce1c(lVar9,"ACT:NO;REGN:NO");
        }
        else if (bVar21 == 1) {
          FUN_14000ce1c(lVar9,"ACT:YES;REGN:NO");
        }
        else if (bVar21 == 2) {
          FUN_14000ce1c(lVar9,"ACT:NO;REGN:YES");
        }
        else if (bVar21 == 3) {
          FUN_14000ce1c(lVar9,"ACT:YES;REGN:YES");
        }
        else {
          FUN_14000ce1c(lVar9,&DAT_1401aa544);
        }
      }
      if ((*param_6 & (byte)(1 << (bVar19 + 1 & 0x1f))) != 0) {
        FUN_140003cf0(local_c8 + lVar25,"B%dS1:",iVar17);
        FUN_140003cf0(&local_e8,"%1.2f",(double)(int)CONCAT11(*pbVar23,pbVar23[1]) * DAT_1401bdfd8);
        FUN_14000d230(local_c8 + lVar25,local_e8,*(undefined4 *)(local_e8 + -0x10));
        FUN_140001834(local_a8 + lVar25,&DAT_1401ab59c);
      }
      iVar17 = iVar17 + 1;
      pbVar23 = pbVar23 + 3;
      bVar19 = bVar19 + 2;
      lVar25 = lVar25 + 0x10;
      local_90 = local_90 + -1;
    } while (local_90 != 0);
    local_90 = 0;
    break;
  case 0x97:
    FUN_140001870(&local_e0,0x5f6);
    iVar17 = 0;
    do {
      if (iVar17 == 0) {
        FUN_140001834(&local_d0,"NOx eng. out:");
      }
      else {
        FUN_140001834(&local_d0 + iVar17,"NOx tailpipe");
      }
      iVar7 = iVar17 + 1;
      if ((*param_6 & (byte)iVar7) == 0) {
        FUN_140001834(&local_e8,&DAT_14019b574);
        FUN_14000d230(&local_d0 + iVar17,local_e8,*(undefined4 *)(local_e8 + -0x10));
        FUN_140001834(local_b0 + (longlong)iVar17 * 8,&DAT_14019aa80);
      }
      else {
        lVar25 = (longlong)iVar17;
        FUN_140003cf0(&local_e8,"%1.4f",
                      ((double)((uint)param_6[lVar25 * 2 + 1] << 8) +
                      (double)param_6[lVar25 * 2 + 2]) * _DAT_1401c1678);
        FUN_14000d230(&local_d0 + lVar25,local_e8,*(undefined4 *)(local_e8 + -0x10));
        FUN_140001834(local_b0 + lVar25 * 8,&DAT_1401ab574);
      }
      iVar17 = iVar7;
    } while (iVar7 < 2);
    break;
  case 0x98:
  case 0x99:
    FUN_140001870(&local_e8,0x44e);
    uVar32 = CONCAT44(uVar33,8);
    FUN_140003cf0(&local_e0,local_e8,param_5 + -0x97,5,uVar32);
    if ((*param_6 & 0xf) == 0) {
      FUN_140001834(&local_d0,&DAT_14019b574);
      FUN_140001834(local_b0,&DAT_14019aa80);
    }
    else {
      iVar17 = 0;
      lVar25 = 0;
      pbVar23 = param_6;
      do {
        lVar9 = (longlong)&local_d0 + lVar25;
        FUN_140003cf0(lVar9,"B%dS%d:",param_5 + -0x97,iVar17 + 5,uVar32);
        if ((*param_6 & (byte)(1 << ((byte)iVar17 & 0x1f))) == 0) {
          if (iVar17 < 2) {
            FUN_140001834(&local_e8,&DAT_14019b574);
            FUN_14000d230(lVar9,local_e8,*(undefined4 *)(local_e8 + -0x10));
            FUN_140001834(local_b0 + lVar25,&DAT_14019aa80);
          }
        }
        else {
          FUN_140003cf0(&local_e8,"%1.2f",
                        ((double)((uint)pbVar23[1] << 8) + (double)pbVar23[2]) * _DAT_1401bdfc8 -
                        DAT_1401c16e8);
          FUN_14000d230(lVar9,local_e8,*(undefined4 *)(local_e8 + -0x10));
          FUN_140001834(local_b0 + lVar25,"mg/m3");
        }
        iVar17 = iVar17 + 1;
        lVar25 = lVar25 + 8;
        pbVar23 = pbVar23 + 2;
      } while (iVar17 < 4);
    }
    break;
  case 0x9a:
    FUN_140001870(&local_e0,0x5f7);
    iVar17 = 0;
    do {
      if ((*param_6 & (byte)(1 << ((byte)iVar17 & 0x1f))) == 0) {
        FUN_140001834(&local_e8,&DAT_14019b574);
        FUN_14000d230(&local_d0 + iVar17,local_e8,*(undefined4 *)(local_e8 + -0x10));
        FUN_140001834(local_b0 + (longlong)iVar17 * 8,&DAT_14019aa80);
      }
      else if (iVar17 == 0) {
        FUN_140001834(&local_d0,"HEV mode:");
        if ((param_6[1] & 1) == 0) {
          FUN_14000ce1c(&local_d0,&DAT_1401b2e18);
        }
        else {
          FUN_14000ce1c(&local_d0,&DAT_1401b2e14);
        }
      }
      else if (iVar17 == 1) {
        FUN_140003cf0(local_c8,"BATT_V: %1.2f",
                      (double)((uint)param_6[2] * 0x100 + (uint)param_6[3]) * _DAT_1401c1670);
        FUN_140001834(local_a8,&DAT_1401ab500);
      }
      else if (iVar17 == 2) {
        FUN_140003cf0(local_c0,"BATT_A: %1.2f",
                      (double)(int)CONCAT11(param_6[4],param_6[5]) * _DAT_1401bdfc8);
        FUN_140001834(local_a0,&DAT_1401ab508);
      }
      else if (iVar17 == 3) {
        FUN_140001834(local_b8,"HEV mode:");
        bVar19 = param_6[1] >> 1 & 3;
        if ((param_6[1] >> 1 & 3) == 0) {
          FUN_14000ce1c(local_b8,&DAT_1401b2e18);
        }
        else if (bVar19 == 1) {
          FUN_14000ce1c(local_b8,&DAT_1401b2e14);
        }
        else if (bVar19 == 2) {
          FUN_14000ce1c(local_b8,&DAT_1401b2e1c);
        }
        else if (bVar19 == 3) {
          FUN_14000ce1c(local_b8,"not PSA");
        }
      }
      iVar17 = iVar17 + 1;
    } while (iVar17 < 4);
    break;
  case 0x9b:
    FUN_140001870(&local_e0,0x5f8);
    FUN_140001834(&local_d0,"DEF type");
    if ((*param_6 & 1) == 0) {
      FUN_140001834(&local_d0,&DAT_14019b574);
    }
    else {
      bVar19 = *param_6 >> 4;
      if (bVar19 == 0) {
        FUN_140001834(&local_d0,"conc. too high");
      }
      else if (bVar19 == 1) {
        FUN_140001834(&local_d0,"conc. too low");
      }
      else if (bVar19 == 2) {
        FUN_140001834(&local_d0,"is diesel/other");
      }
      else if (bVar19 == 3) {
        FUN_140001834(&local_d0,"conc. proper");
      }
      else if (bVar19 == 0xd) {
        FUN_140001834(&local_d0,"conc. N/A");
      }
      else if (bVar19 == 0xe) {
        FUN_140001834(&local_d0,"sensor err.");
      }
      else {
        FUN_140001834(&local_d0,"reserved");
      }
    }
    if ((*param_6 & 2) == 0) {
      FUN_140001834(local_c8,&DAT_14019b574);
    }
    else {
      FUN_140001834(local_c8,"DEF_CON: ");
      FUN_140003cf0(&local_e8,"%1.2f",(double)param_6[1] * _DAT_1401c1708);
      FUN_140001834(local_a8,&DAT_1401ab59c);
    }
    if ((*param_6 & 4) == 0) {
      FUN_140001834(local_c0,&DAT_14019b574);
    }
    else {
      FUN_140001834(local_c0,"DEF_T: ");
      FUN_140003cf0(&local_e8,"%1.0f",(double)param_6[2] - DAT_1401c16e8);
      FUN_140001834(local_a0,&DAT_1401ab434);
    }
    if ((*param_6 & 8) == 0) {
      FUN_140001834(local_b8,&DAT_14019b574);
    }
    else {
      FUN_140001870(local_b8,0xf0);
      FUN_140003cf0(&local_e8," %1.0f",(double)param_6[3] * DAT_1401bdf00 * _DAT_1401bdfe8);
      FUN_14000d230(local_b8,local_e8,*(undefined4 *)(local_e8 + -0x10));
      FUN_140001834(local_98,&DAT_1401ab59c);
    }
    break;
  case 0x9d:
    FUN_140001870(&local_e0,0x5f9);
    FUN_140001834(&local_d0,"Engine: ");
    FUN_140001834(local_c8,"Vehicle: ");
    lVar25 = 0;
    lVar9 = 2;
    do {
      FUN_140003cf0(&local_e8,"%1.2f",
                    ((double)((uint)*param_6 << 8) + (double)param_6[1]) * _DAT_1401c1668);
      FUN_14000d230((longlong)&local_d0 + lVar25,local_e8,*(undefined4 *)(local_e8 + -0x10));
      FUN_140001834(local_b0 + lVar25,&DAT_1401ab574);
      param_6 = param_6 + 2;
      lVar25 = lVar25 + 8;
      lVar9 = lVar9 + -1;
    } while (lVar9 != 0);
    break;
  case 0x9e:
    FUN_140001870(&local_e0,0x5fa);
    FUN_140001834(local_b0,&DAT_1401b2f04);
    FUN_140003cf0(&local_d0,"%1.1f",
                  ((double)((uint)*param_6 << 8) + (double)param_6[1]) * _DAT_1401be040);
    break;
  case 0xa2:
    FUN_140001870(&local_e0,0x5fb);
    FUN_140001834(local_b0,"mg/str");
    FUN_140003cf0(&local_d0,"%1.2f",
                  ((double)((uint)*param_6 << 8) + (double)param_6[1]) * _DAT_1401c16b0);
    break;
  case 0xa4:
    FUN_140001870(&local_e0,0x5fc);
    FUN_140001834(&local_d0,"Act. gear: ");
    if ((*param_6 & 1) == 0) {
      FUN_140001834(&local_d0,&DAT_14019b574);
    }
    else if (param_6[1] >> 4 == 0) {
      FUN_140001834(&local_d0,&DAT_1401ab4c8);
    }
    else if (param_6[1] >> 4 == 1) {
      FUN_140001834(&local_d0,&DAT_1401b2f24);
    }
    else {
      FUN_140003cf0(&local_d0,&DAT_14019b2e8);
    }
    if ((*param_6 & 2) != 0) {
      FUN_140001834(local_c8,"Ratio: ");
      FUN_140003cf0(&local_d0,"%1.3f",
                    ((double)((uint)param_6[2] << 8) + (double)param_6[3]) * DAT_1401bdfa8);
    }
    break;
  case 0xa6:
    FUN_140001870(&local_e0,0x5fd);
    FUN_140001834(local_b0,&DAT_1401b29b0);
    lVar9 = 0;
    lVar25 = 4;
    do {
      lVar9 = lVar9 * 0x100 + (ulonglong)*param_6;
      param_6 = param_6 + 1;
      lVar25 = lVar25 + -1;
    } while (lVar25 != 0);
    dVar29 = (double)lVar9;
    if (lVar9 < 0) {
      dVar29 = dVar29 + _DAT_1401c1698;
    }
    FUN_140003cf0(&local_d0,"%1.1f",dVar29 * _DAT_1401bdfc8);
  }
  FID_conflict_operator_(param_2,&local_e0);
  lVar9 = 0;
  lVar25 = 4;
  do {
    lVar26 = (longlong)&local_d0 + lVar9;
    FID_conflict_operator_(*(undefined8 *)(lVar26 + (param_3 - (longlong)&local_d0)),lVar26);
    FID_conflict_operator_
              (*(undefined8 *)((longlong)param_4 + (lVar26 - (longlong)&local_d0)),local_b0 + lVar9)
    ;
    lVar9 = lVar9 + 8;
    lVar25 = lVar25 + -1;
  } while (lVar25 != 0);
  LOCK();
  piVar1 = (int *)(local_d8 + -1);
  iVar17 = *piVar1;
  *piVar1 = *piVar1 + -1;
  UNLOCK();
  if (iVar17 + -1 < 1) {
    (**(code **)(*(longlong *)local_d8[-3] + 8))();
  }
  LOCK();
  piVar1 = (int *)(local_e8 + -8);
  iVar17 = *piVar1;
  *piVar1 = *piVar1 + -1;
  UNLOCK();
  if (iVar17 + -1 < 1) {
    (**(code **)(**(longlong **)(local_e8 + -0x18) + 8))();
  }
  LOCK();
  piVar1 = (int *)(local_e0 + -8);
  iVar17 = *piVar1;
  *piVar1 = *piVar1 + -1;
  UNLOCK();
  if (iVar17 + -1 < 1) {
    (**(code **)(**(longlong **)(local_e0 + -0x18) + 8))();
  }
  _eh_vector_destructor_iterator_(local_b0,8,4,FUN_1400b2e64);
  _eh_vector_destructor_iterator_(&local_d0,8,4,FUN_1400b2e64);
  return 0;
}


```


### String `"\"Type 16 - Vehicle Operation Data - Engine Run-Idle Time, Recent/Lifetime:\""` at `1401b36c0`
- *(No direct XREFs found)*

### String `"\"Fueled Engine Operation Ignition Cycle Counter\""` at `1401b3728`
- *(No direct XREFs found)*

### String `"\"Total Engine Run Time\""` at `1401b3758`
- *(No direct XREFs found)*

### String `"\"Total Idle Engine Run Time\""` at `1401b3788`
- *(No direct XREFs found)*

### String `"\"Engine Output Energy\""` at `1401b38e0`
- *(No direct XREFs found)*

### String `"\"Engine Running Coasting Timer\""` at `1401b3a50`
- *(No direct XREFs found)*

### String `"\"Engine Off Coasting Timer\""` at `1401b3a70`
- *(No direct XREFs found)*

### String `"\"Active Engine Warm-up Timer\""` at `1401b3ae0`
- *(No direct XREFs found)*

### String `"\"VCDS generated debug information\nregarding the missing VIN issue (DEBUG-VIN.DLM).\nWould you like VCDS to open its location\nso you can send it to support@ross-tech.com?\""` at `1401b4120`
- **XREF from non-function address:** `1400d1452`
- **XREF from non-function address:** `1400d1461`

### String `"\": CAN\""` at `1401b4374`
- *(No direct XREFs found)*

### String `"\"CANTimeOut=\""` at `1401b56f8`
- **XREF from:** `1400e7d6c` in Function **`FUN_1400e7cd8`** (`1400e7cd8`)

```c
// Function: FUN_1400e7cd8 @ 1400e7cd8

void FUN_1400e7cd8(char *param_1)

{
  int iVar1;
  undefined4 *puVar2;
  
  if (*param_1 != ';') {
    iVar1 = strncmp(param_1,"Port=COM",8);
    if (iVar1 == 0) {
      FUN_1400e8260();
    }
    else {
      iVar1 = strncmp(param_1,"BInt=",5);
      if (iVar1 == 0) {
        FUN_1400e82c0();
      }
      else {
        iVar1 = strncmp(param_1,"Baud=",5);
        if (iVar1 == 0) {
          FUN_1400e82fc();
        }
        else {
          iVar1 = strncmp(param_1,"CANTimeOut=",0xb);
          if (iVar1 == 0) {
            FUN_1400e833c();
          }
          else {
            iVar1 = strncmp(param_1,"CInt=",5);
            if (iVar1 == 0) {
              FUN_1400e8378();
            }
            else {
              iVar1 = strncmp(param_1,"KW2Delay=",9);
              if (iVar1 == 0) {
                FUN_1400e83b0();
              }
              else {
                iVar1 = strncmp(param_1,"HexIntel=",9);
                if (iVar1 == 0) {
                  FUN_1400e83e8();
                }
                else {
                  iVar1 = strncmp(param_1,"BypassFastInit=",0xb);
                  if (iVar1 == 0) {
                    FUN_1400e83f8();
                  }
                  else {
                    iVar1 = strncmp(param_1,"NETTimeOut=",0xb);
                    if (iVar1 == 0) {
                      FUN_1400e8408();
                    }
                    else {
                      iVar1 = strncmp(param_1,"SavedIP=",8);
                      if (iVar1 == 0) {
                        FUN_1400e8444();
                      }
                      else {
                        iVar1 = strncmp(param_1,"LastCB=",7);
                        if (iVar1 == 0) {
                          FUN_1400e84b8();
                        }
                        else {
                          iVar1 = strncmp(param_1,"CInt2k=",5);
                          if (iVar1 == 0) {
                            FUN_1400e8524();
                          }
                          else {
                            iVar1 = strncmp(param_1,"WSC=",4);
                            if (iVar1 == 0) {
                              FUN_1400e855c();
                            }
                            else {
                              iVar1 = strncmp(param_1,"Geraet=",7);
                              if (iVar1 == 0) {
                                FUN_1400e8604();
                              }
                              else {
                                iVar1 = strncmp(param_1,"IMP=",4);
                                if (iVar1 == 0) {
                                  FUN_1400e85b0();
                                }
                                else {
                                  iVar1 = strncmp(param_1,"RedCPU1=",8);
                                  if (iVar1 == 0) {
                                    puVar2 = &DAT_140631f0c;
                                  }
                                  else {
                                    iVar1 = strncmp(param_1,"RedCPU2=",8);
                                    if (iVar1 != 0) {
                                      iVar1 = strncmp(param_1,"KP2Time=",8);
                                      if (iVar1 == 0) {
                                        FUN_1400e81d0();
                                        return;
                                      }
                                      iVar1 = strncmp(param_1,"Sound=",5);
                                      if (iVar1 == 0) {
                                        FUN_1400e8208();
                                        return;
                                      }
                                      iVar1 = strncmp(param_1,"Verbau=",6);
                                      if (iVar1 == 0) {
                                        FUN_1400e8220();
                                        return;
                                      }
                                      iVar1 = strncmp(param_1,"NoBlkScr=",8);
                                      if (iVar1 == 0) {
                                        FUN_1400e8238();
                                        return;
                                      }
                                      iVar1 = strncmp(param_1,"Shop=",5);
                                      if (iVar1 == 0) {
                                        FUN_140156be0(&DAT_140630ca0,param_1 + 5);
                                        return;
                                      }
                                      iVar1 = strncmp(param_1,"PrintMargin=",0xc);
                                      if (iVar1 == 0) {
                                        FUN_1400e8198();
                                        return;
                                      }
                                      iVar1 = strncmp(param_1,"ForceK=",7);
                                      if (iVar1 == 0) {
                                        FUN_1400e8658();
                                        return;
                                      }
                                      iVar1 = strncmp(param_1,"BypassCAN=",10);
                                      if (iVar1 == 0) {
                                        FUN_1400e86a4();
                                        return;
                                      }
                                      iVar1 = strncmp(param_1,"BypassK=",8);
                                      if (iVar1 == 0) {
                                        FUN_1400e86b4();
                                        return;
                                      }
                                      iVar1 = strncmp(param_1,"BypassDoIP=",0xb);
                                      if (iVar1 == 0) {
                                        FUN_1400e86c4();
                                        return;
                                      }
                                      iVar1 = strncmp(param_1,"LastInterface=",0xe);
                                      if (iVar1 == 0) {
                                        FUN_1400e86d4();
                                        return;
                                      }
                                      iVar1 = strncmp(param_1,"Size=",5);
                                      if (iVar1 == 0) {
                                        FUN_1400e8668();
                                        return;
                                      }
                                      iVar1 = strncmp(param_1,"Updates=",8);
                                      if (iVar1 == 0) {
                                        FUN_1400e870c();
                                        return;
                                      }
                                      iVar1 = strncmp(param_1,"LastVer=",8);
                                      if (iVar1 == 0) {
                                        FUN_1400e8744();
                                        return;
                                      }
                                      iVar1 = strncmp(param_1,"NPIC=",5);
                                      if (iVar1 == 0) {
                                        FUN_1400e8780();
                                        return;
                                      }
                                      iVar1 = strncmp(param_1,"NetSerial=",10);
                                      if (iVar1 != 0) {
                                        return;
                                      }
                                      FUN_1400e847c(param_1);
                                      return;
                                    }
                                    puVar2 = &DAT_140631f10;
                                  }
                                  FUN_1400e8250(param_1,puVar2);
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
  return;
}


```


### String `"\"BypassCAN=\""` at `1401b57f8`
- **XREF from:** `1400e8038` in Function **`FUN_1400e7cd8`** (`1400e7cd8`)

### String `"\"CANTimeOut=%s\n\""` at `1401b59c0`
- *(No direct XREFs found)*

### String `"\"BypassCAN=%d\n\""` at `1401b5b18`
- *(No direct XREFs found)*

### String `"\"Ross-Tech Updater\""` at `1401b5bc0`
- *(No direct XREFs found)*

### String `"\"Please send an autoscan.\""` at `1401b9810`
- **XREF from non-function address:** `1400fe880`
- **XREF from non-function address:** `1400fe88f`

### String `"\"ClearAll-CAN\""` at `1401bba18`
- *(No direct XREFs found)*

### String `"\"AutoMode %d,KW2Delay %d, BlkInt %d, CharInt %d, KP2Time %d, CANTO %d\""` at `1401bbe70`
- **XREF from:** `140116788` in Function **`FUN_14011635c`** (`14011635c`)

```c
// Function: FUN_14011635c @ 14011635c

void FUN_14011635c(longlong param_1,char *param_2)

{
  int *piVar1;
  int iVar2;
  byte bVar3;
  byte bVar4;
  byte bVar5;
  longlong *plVar6;
  undefined8 *puVar7;
  char *pcVar8;
  size_t sVar9;
  undefined8 uVar10;
  undefined8 *puVar11;
  uint uVar12;
  ulonglong uVar13;
  uint local_res18 [2];
  undefined8 *local_res20;
  undefined8 in_stack_ffffffffffffff68;
  undefined4 uVar15;
  undefined8 uVar14;
  undefined8 in_stack_ffffffffffffff70;
  undefined4 uVar17;
  undefined4 uVar18;
  undefined8 uVar16;
  longlong local_68;
  longlong local_60;
  longlong local_58;
  undefined8 local_50;
  char acStack_4c [4];
  char local_48 [32];
  
  uVar15 = (undefined4)((ulonglong)in_stack_ffffffffffffff68 >> 0x20);
  uVar17 = (undefined4)((ulonglong)in_stack_ffffffffffffff70 >> 0x20);
  local_50 = 0xfffffffffffffffe;
  plVar6 = (longlong *)FUN_14013a630();
  if (plVar6 == (longlong *)0x0) {
                    /* WARNING: Subroutine does not return */
    FUN_140001000(0x80004005);
  }
  puVar7 = (undefined8 *)(**(code **)(*plVar6 + 0x18))(plVar6);
  local_res20 = puVar7 + 3;
  if ((DAT_140631f20 < 1000) && (pcVar8 = strstr(param_2,"DEBUG-CAL"), pcVar8 == (char *)0x0)) {
    LOCK();
    piVar1 = (int *)(puVar7 + 2);
    iVar2 = *piVar1;
    *piVar1 = *piVar1 + -1;
    UNLOCK();
    if (0 < iVar2 + -1) {
      return;
    }
    (**(code **)(*(longlong *)*puVar7 + 8))();
    return;
  }
  DAT_1405a55d0 = FUN_140156994(param_2,&DAT_14019af8c);
  if (DAT_1405a55d0 == 0) {
    DAT_140631f20 = 0;
  }
  else {
    FUN_140156be0(&DAT_1405a5550,param_2);
  }
  FUN_140112e94(param_1,0,0);
  (*DAT_14018c728)(&DAT_140631ea0);
  sprintf(&DAT_1405a5df0,"\nt%04d VCDS Version %s (%s) / %d.%d.%d.%d",
          (ulonglong)(DAT_140631ea0 & 0x1fff),&DAT_1405a4260,&DAT_14019c7d4,
          *(undefined4 *)(param_1 + 0x3cc),CONCAT44(uVar15,*(undefined4 *)(param_1 + 0x3d0)),
          CONCAT44(uVar17,*(undefined4 *)(param_1 + 0x3d4)),*(undefined4 *)(param_1 + 0x3d8));
  FUN_1400a1378(1);
  (*DAT_14018c728)(local_res18);
  sprintf(&DAT_1405a5df0,"\nt%04d ",(ulonglong)(local_res18[0] & 0x1fff));
  FUN_140115558(param_1,&DAT_1405a59f0,1);
  FUN_140156b40(&DAT_1405a5df0,&DAT_1405a59f0);
  FUN_1400a1378(1);
  (*DAT_14018c728)(local_res18);
  sprintf(&DAT_1405a5df0,"\nt%04d ",(ulonglong)(local_res18[0] & 0x1fff));
  sVar9 = strlen(" ");
  FUN_140001a34(&local_res20,&DAT_14019b56c,sVar9 & 0xffffffff);
  local_68 = FUN_140001b48(local_res20 + -3);
  local_68 = local_68 + 0x18;
  uVar10 = FUN_1401206c4(param_1,&local_60,&local_68);
  FID_conflict_operator_(&local_res20,uVar10);
  LOCK();
  piVar1 = (int *)(local_60 + -8);
  iVar2 = *piVar1;
  *piVar1 = *piVar1 + -1;
  UNLOCK();
  if (iVar2 + -1 < 1) {
    (**(code **)(**(longlong **)(local_60 + -0x18) + 8))();
  }
  if (*(int *)(local_res20 + -2) == 1) {
    sVar9 = strlen("NONE");
    FUN_140001a34(&local_res20,&DAT_1401bbe18,sVar9 & 0xffffffff);
  }
  puVar7 = local_res20;
  puVar11 = (undefined8 *)FUN_14011e5bc(param_1,&local_58);
  uVar10 = *(undefined8 *)(param_1 + 0x7b0);
  uVar14 = *puVar11;
  sprintf(&DAT_1405a59f0,"Computer: %s %s; VM %s; OS 0x%05X (%s); WMI:%s",
          *(undefined8 *)(param_1 + 0x718),*(undefined8 *)(param_1 + 0x710),puVar7,
          *(undefined4 *)(param_1 + 0x704),uVar14,uVar10);
  LOCK();
  piVar1 = (int *)(local_58 + -8);
  iVar2 = *piVar1;
  *piVar1 = *piVar1 + -1;
  UNLOCK();
  if (iVar2 + -1 < 1) {
    (**(code **)(**(longlong **)(local_58 + -0x18) + 8))();
  }
  FUN_140156b40(&DAT_1405a5df0,&DAT_1405a59f0);
  FUN_1400a1378(1);
  (*DAT_14018c728)(local_res18);
  sprintf(&DAT_1405a5df0,"\nt%04d ",(ulonglong)(local_res18[0] & 0x1fff));
  sprintf(&DAT_1405a59f0,"%d MB ; %s",(ulonglong)*(uint *)(param_1 + 0x7b8),
          *(undefined8 *)(param_1 + 0x720));
  FUN_140156b40(&DAT_1405a5df0,&DAT_1405a59f0);
  FUN_1400a1378(1);
  (*DAT_14018c728)(local_res18);
  sprintf(&DAT_1405a5df0,"\nt%04d ",(ulonglong)(local_res18[0] & 0x1fff));
  uVar13 = 0;
  plVar6 = (longlong *)(param_1 + 0x730);
  do {
    if (*(int *)(*plVar6 + -0x10) != 0) {
      sprintf(&DAT_1405a59f0,"MAC%d: %s    ",uVar13);
      FUN_140156b40(&DAT_1405a5df0,&DAT_1405a59f0);
    }
    uVar15 = (undefined4)((ulonglong)puVar7 >> 0x20);
    uVar17 = (undefined4)((ulonglong)uVar14 >> 0x20);
    uVar18 = (undefined4)((ulonglong)uVar10 >> 0x20);
    uVar12 = (int)uVar13 + 1;
    uVar13 = (ulonglong)uVar12;
    plVar6 = plVar6 + 1;
  } while ((int)uVar12 < 0x10);
  FUN_1400a1378(1);
  (*DAT_14018c728)(local_res18);
  sprintf(&DAT_1405a5df0,"\nt%04d ",(ulonglong)(local_res18[0] & 0x1fff));
  uVar16 = CONCAT44(uVar18,DAT_140631efc);
  uVar14 = CONCAT44(uVar17,DAT_1401f3578);
  uVar10 = CONCAT44(uVar15,DAT_1401f357c);
  sprintf(&DAT_1405a59f0,"AutoMode %d,KW2Delay %d, BlkInt %d, CharInt %d, KP2Time %d, CANTO %d",
          (ulonglong)DAT_140631ed0,(ulonglong)DAT_1401f3564,uVar10,DAT_1401f3568,uVar14,uVar16);
  uVar15 = (undefined4)((ulonglong)uVar10 >> 0x20);
  uVar17 = (undefined4)((ulonglong)uVar14 >> 0x20);
  uVar18 = (undefined4)((ulonglong)uVar16 >> 0x20);
  FUN_140156b40(&DAT_1405a5df0,&DAT_1405a59f0);
  FUN_1400a1378(1);
  (*DAT_14018c728)(local_res18);
  sprintf(&DAT_1405a5df0,"\nt%04d ",(ulonglong)(local_res18[0] & 0x1fff));
  uVar14 = CONCAT44(uVar18,DAT_1401f358c);
  uVar10 = CONCAT44(uVar17,DAT_140631f4c);
  sprintf(&DAT_1405a59f0,
          "RedCPU1281 %d, RedCPU2K %d, ForceK %d, BpCAN/K/DoIP %d/%d/%d, COM %d, Upd %d",
          (ulonglong)DAT_140631f0c,(ulonglong)DAT_140631f10,CONCAT44(uVar15,DAT_140631f44),
          DAT_140631f48,uVar10,uVar14,DAT_1405a55e4,DAT_1401f35a0);
  uVar17 = (undefined4)((ulonglong)uVar10 >> 0x20);
  uVar18 = (undefined4)((ulonglong)uVar14 >> 0x20);
  FUN_140156b40(&DAT_1405a5df0,&DAT_1405a59f0);
  FUN_1400a1378(1);
  (*DAT_14018c728)(local_res18);
  sprintf(&DAT_1405a5df0,"\nt%04d ",(ulonglong)(local_res18[0] & 0x1fff));
  FID_conflict_operator_(&local_res20,DAT_140631e78 + 5);
  FUN_14004d0dc(&local_res20,10);
  puVar7 = local_res20;
  uVar10 = *(undefined8 *)(param_1 + 0x378);
  sprintf(&DAT_1405a59f0,"%s %s %s",DAT_140631e78[3],local_res20,uVar10);
  uVar15 = (undefined4)((ulonglong)uVar10 >> 0x20);
  FUN_140156b40(&DAT_1405a5df0,&DAT_1405a59f0);
  FUN_1400a1378(1);
  local_48[0] = '\0';
  (**(code **)(*DAT_140631e78 + 0x10))(DAT_140631e78,local_48);
  sVar9 = strlen(local_48);
  if (4 < sVar9) {
    sVar9 = strlen(local_48);
    local_48[sVar9 - 4] = '\0';
  }
  (*DAT_14018c728)(local_res18);
  sprintf(&DAT_1405a5df0,"\nt%04d ",(ulonglong)(local_res18[0] & 0x1fff));
  bVar3 = *(byte *)((longlong)DAT_140631e78 + 0x4d);
  bVar4 = FUN_140118ab0(param_1);
  bVar5 = FUN_14011726c(param_1);
  uVar16 = CONCAT44(uVar18,(uint)bVar3);
  uVar14 = CONCAT44(uVar17,*(undefined4 *)((longlong)DAT_140631e78 + 0x54));
  uVar10 = CONCAT44(uVar15,(uint)bVar4);
  sprintf(&DAT_1405a59f0,"%s CS:%d CS2:%d CY:%d FR:%d SU:%d SUF:%d CL2:%d SGN:%d ",local_48,
          (ulonglong)bVar5,uVar10,(int)DAT_140631e78[10],uVar14,uVar16,DAT_1405a5548,DAT_140631ed4,
          DAT_140631ed8);
  uVar15 = (undefined4)((ulonglong)uVar10 >> 0x20);
  uVar17 = (undefined4)((ulonglong)uVar14 >> 0x20);
  uVar18 = (undefined4)((ulonglong)uVar16 >> 0x20);
  FUN_140156b40(&DAT_1405a5df0,&DAT_1405a59f0);
  FUN_1400a1378(1);
  if (DAT_1405a55e4 == 7) {
    (*DAT_14018c728)(local_res18);
    sprintf(&DAT_1405a5df0,"\nt%04d ",(ulonglong)(local_res18[0] & 0x1fff));
    uVar12 = *(uint *)(DAT_140631e78 + 0x2b);
    sprintf(&DAT_1405a59f0,"Net Timeout %d, SavedIP %d.%d.%d.%d, ActualIP %d.%d.%d.%d",
            (ulonglong)DAT_1401f35a8,(ulonglong)DAT_140631f50._3_1_,
            CONCAT44(uVar15,(uint)DAT_140631f50._2_1_),(uint)DAT_140631f50._1_1_,
            CONCAT44(uVar17,(uint)(byte)DAT_140631f50),CONCAT44(uVar18,uVar12) & 0xffffffff000000ff,
            uVar12 >> 8 & 0xff,uVar12 >> 0x10 & 0xff,uVar12 >> 0x18);
    FUN_140156b40(&DAT_1405a5df0,&DAT_1405a59f0);
    FUN_1400a1378(1);
  }
  LOCK();
  piVar1 = (int *)(puVar7 + -1);
  iVar2 = *piVar1;
  *piVar1 = *piVar1 + -1;
  UNLOCK();
  if (iVar2 + -1 < 1) {
    (**(code **)(*(longlong *)puVar7[-3] + 8))();
  }
  return;
}


```


### String `"\"RedCPU1281 %d, RedCPU2K %d, ForceK %d, BpCAN/K/DoIP %d/%d/%d, COM %d, Upd %d\""` at `1401bbec0`
- **XREF from:** `140116819` in Function **`FUN_14011635c`** (`14011635c`)

### String `"\"Can't allocate memory\""` at `1401bc038`
- **XREF from:** `14011d5f1` in Function **`FUN_14011d5c4`** (`14011d5c4`)

```c
// Function: FUN_14011d5c4 @ 14011d5c4

longlong FUN_14011d5c4(longlong param_1)

{
  longlong lVar1;
  char *pcVar2;
  
  lVar1 = (*DAT_14018c648)(2);
  *(longlong *)(param_1 + 0x3b0) = lVar1;
  if (lVar1 == 0) {
    pcVar2 = "Can\'t allocate memory";
  }
  else {
    lVar1 = (*DAT_14018c758)(lVar1);
    if (lVar1 != 0) {
      return lVar1;
    }
    pcVar2 = "Can\'t lock memory";
  }
  AfxMessageBox(pcVar2,0,0);
  return 0;
}


```


### String `"\"Can't lock memory\""` at `1401bc050`
- **XREF from:** `14011d608` in Function **`FUN_14011d5c4`** (`14011d5c4`)

### String `"\"Can't unlock memory\""` at `1401bc068`
- **XREF from:** `14011d644` in Function **`FUN_14011d624`** (`14011d624`)

### String `"\"Cannot empty the Clipboard\""` at `1401bc080`
- **XREF from:** `14011d688` in Function **`FUN_14011d624`** (`14011d624`)

### String `"\"How to Copy and Paste\n\nVAG-COM is a program that runs on Microsoft Windows.\nIn order to perform certain functions, you may need to use\nthe Copy and Paste functions.\n\nYour mouse or pointing device may not look exactly like the one below.\nIf it does not, please see the owner's manual for your mouse or computer to find out\nhow to move the cursor and where the left and right mouse buttons are located.\n\n    *\n\n      Using your mouse, place your cursor at the beginning of the text in the first box below.\n    *\n\n      Hold down the left mouse button, while dragging your cursor over the text by moving the mouse to the right until you reach the end of the text.\n    *\n\n      This should highlight the text. Now release the left mouse button.\n    *\n\n      Next using your mouse, place your cursor over the highlighted text.\n    *\n\n      Press and release the right mouse button and a list of options will appear.\n    *\n\n      Using your mouse, move the cursor up or down the list until it highlights 'Copy'.\n    *\n\n      Press and release the left mouse button.\n    *\n\n      You have just copied the text! It has been copied to an invisible clipboard.\n    *\n\n       Now using your mouse, place your cursor over the second box below (the empty one).\n    *\n\n       Press and release the right mouse button and a list of options will appear.\n    *\n\n       Using your mouse, move the cursor up or down the list until it highlights 'Paste'.\n    *\n\n       Press and release the left mouse button.\n    *\n\n       You have just pasted the text!  You can now copy and paste, even between different programs!\""` at `14020b1e0`
- *(No direct XREFs found)*

### String `"\"C:\\Ross-Tech\\VCDS\\VCDS.EXE\""` at `140214f60`
- **XREF from:** `14016bf95` in Function **`_setargv`** (`14016bf78`)

```c
// Function: _setargv @ 14016bf78

/* Library Function - Single Match
    _setargv
   
   Library: Visual Studio 2008 Release */

int __cdecl _setargv(void)

{
  ulonglong _Size;
  void *pvVar1;
  ulonglong uVar2;
  char *pcVar3;
  ulonglong uVar4;
  int local_res8 [2];
  int local_res10 [2];
  
  if (DAT_1525d7a38 == 0) {
    __initmbctable();
  }
  DAT_140215064 = 0;
  (*DAT_14018c710)(0,s_C__Ross_Tech_VCDS_VCDS_EXE_140214f60,0x104);
  PTR_s_C__Ross_Tech_VCDS_VCDS_EXE_140214938 = s_C__Ross_Tech_VCDS_VCDS_EXE_140214f60;
  if ((DAT_1525d7a48 == (char *)0x0) || (pcVar3 = DAT_1525d7a48, *DAT_1525d7a48 == '\0')) {
    pcVar3 = s_C__Ross_Tech_VCDS_VCDS_EXE_140214f60;
  }
  parse_cmdline(pcVar3,0,0,local_res8,local_res10);
  uVar4 = (ulonglong)local_res8[0];
  if ((((uVar4 < 0x1fffffffffffffff) &&
       (uVar2 = (ulonglong)local_res10[0], uVar2 != 0xffffffffffffffff)) &&
      (_Size = uVar2 + uVar4 * 8, uVar2 <= _Size)) &&
     (pvVar1 = _malloc_crt(_Size), pvVar1 != (void *)0x0)) {
    parse_cmdline(pcVar3,pvVar1,(void *)((longlong)pvVar1 + uVar4 * 8),local_res8,local_res10);
    DAT_140214904 = local_res8[0] + -1;
    DAT_140214908 = pvVar1;
    return 0;
  }
  return -1;
}


```

- **XREF from:** `14016bfa4` in Function **`_setargv`** (`14016bf78`)
- **XREF from:** `14016bfde` in Function **`_setargv`** (`14016bf78`)
- **XREF from:** `14016c034` in Function **`_setargv`** (`14016bf78`)
- **XREF from non-function address:** `140214938`

## 2. Low-Level FTDI D2XX Call Sites and Hardware Wrappers

### FTDI Wrapper Function: `FUN_1401117bc` @ `1401117bc`
**Callers:**
- `FUN_140111e70` @ `140111e70`

```c

ulonglong FUN_1401117bc(void)

{
  int iVar1;
  int local_res8 [8];
  byte local_18 [24];
  
  DAT_140630f90 = (*DAT_14018c890)(DAT_140630f48,local_res8);
  if (((DAT_140630f90 != 0) || (iVar1 = 0, local_res8[0] != 0)) &&
     (iVar1 = (*DAT_14018c858)(DAT_140630f48,local_18,1,local_res8), local_res8[0] != 0)) {
    if (iVar1 == 0) {
      DAT_140630f90 = iVar1;
      return (ulonglong)local_18[0];
    }
    DAT_140630f90 = iVar1;
    return 0xfffffffe;
  }
  DAT_140630f90 = iVar1;
  return 0xffffff9c;
}


```

### FTDI Wrapper Function: `FUN_140111828` @ `140111828`
**Callers:**
- `FUN_140111e4c` @ `140111e4c`

```c

int FUN_140111828(undefined1 param_1)

{
  undefined1 local_res8 [32];
  undefined1 local_18 [24];
  
  local_18[0] = param_1;
  DAT_140630f90 = (*DAT_14018c860)(DAT_140630f48,local_18,1,local_res8);
  return -(uint)(DAT_140630f90 != 0);
}


```

### FTDI Wrapper Function: `FUN_14011185c` @ `14011185c`
**Callers:**
- `FUN_140111e00` @ `140111e00`

```c

int FUN_14011185c(undefined8 param_1,undefined4 param_2)

{
  undefined1 local_res18 [16];
  
  DAT_140630f90 = (*DAT_14018c860)(DAT_140630f48,param_1,param_2,local_res18);
  return -(uint)(DAT_140630f90 != 0);
}


```

### FTDI Wrapper Function: `FUN_140111888` @ `140111888`
**Callers:**
- `FUN_140111ddc` @ `140111ddc`

```c

int FUN_140111888(void)

{
  DAT_140630f90 = (*DAT_14018c840)(DAT_140630f48,1);
  return -(uint)(DAT_140630f90 != 0);
}


```

### FTDI Wrapper Function: `FUN_140111970` @ `140111970`
**Callers:**
- `FUN_140111ed0` @ `140111ed0`

```c

int FUN_140111970(undefined4 param_1)

{
  DAT_140630f90 = (*DAT_14018c8b8)(DAT_140630f48,param_1);
  (*DAT_14018c840)(DAT_140630f48,3);
  return -(uint)(DAT_140630f90 != 0);
}


```

### FTDI Wrapper Function: `FUN_140111f40` @ `140111f40`
**Callers:**
- `FUN_140111ed0` @ `140111ed0`
- `FUN_140112160` @ `140112160`

```c

undefined4 FUN_140111f40(void)

{
  undefined1 uVar1;
  void *_Memory;
  int iVar2;
  int iVar3;
  undefined4 uVar4;
  int iVar5;
  uint *puVar6;
  uint local_res8 [2];
  
  iVar5 = 0;
  DAT_140630f90 = (*DAT_14018c8c0)(local_res8);
  if (DAT_140630f90 != 0) {
    return 0xffffffff;
  }
  _Memory = malloc((ulonglong)local_res8[0] * 0x68);
  DAT_140630f90 = (*DAT_14018c8d0)();
  if (DAT_140630f90 == 0) {
    iVar2 = 0;
    iVar3 = 0;
    if (0 < (int)local_res8[0]) {
      puVar6 = (uint *)((longlong)_Memory + 8);
      do {
        if (((*puVar6 & 0xffff0000) == 0x4030000) && ((*puVar6 & 0xffff) - 0xfa20 < 0x10)) {
          iVar2 = iVar2 + 1;
          iVar5 = iVar3;
        }
        iVar3 = iVar3 + 1;
        puVar6 = puVar6 + 0x1a;
      } while (iVar3 < (int)local_res8[0]);
      if (1 < iVar2) {
        uVar4 = 0xfffffffd;
        goto LAB_140112123;
      }
      if (iVar2 != 0) {
        FUN_140156be0(&DAT_140630fa0,(longlong)iVar5 * 0x68 + 0x20 + (longlong)_Memory);
        DAT_140630c7c = *(undefined2 *)((longlong)iVar5 * 0x68 + 8 + (longlong)_Memory);
        iVar2 = (*DAT_14018c8a8)(iVar5,&DAT_140630f48);
        DAT_140630f90 = iVar2;
        free(_Memory);
        if (iVar2 != 0) {
          return 0xfffffffb;
        }
        DAT_140630f90 = (*DAT_14018c810)(DAT_140630f48);
        if (DAT_140630f90 != 0) {
          return 0xfffffffa;
        }
        FUN_140112140();
        DAT_140630f90 = (*DAT_14018c8a8)(iVar5,&DAT_140630f48);
        if (DAT_140630f48 == -1) {
          return 0xfffffff8;
        }
        if (((DAT_1405a5544 < 0x2329) || (DAT_1401f6818 != 0x56)) || (uVar1 = 2, DAT_1405a554c == 2)
           ) {
          uVar1 = 1;
        }
        iVar2 = (*DAT_14018c878)(DAT_140630f48,uVar1);
        if (iVar2 != 0) {
          return 0xfffffff7;
        }
        (*DAT_14018c818)(DAT_140630f48);
        DAT_140630f90 = (*DAT_14018c8a8)(iVar5,&DAT_140630f48);
        if (DAT_140630f90 != 0) {
          return 0xfffffff5;
        }
        (*DAT_14018c848)(DAT_140630f48,1,100);
        return 0;
      }
    }
    uVar4 = 0xfffffffe;
  }
  else {
    uVar4 = 0xffffffff;
  }
LAB_140112123:
  free(_Memory);
  return uVar4;
}


```

### FTDI Wrapper Function: `FUN_140112160` @ `140112160`
**Callers:**
- `FUN_1400a1504` @ `1400a1504`

```c

undefined8 FUN_140112160(void)

{
  undefined2 uVar1;
  int iVar2;
  undefined8 uVar3;
  uint local_res8 [2];
  undefined1 local_res10 [8];
  undefined1 local_58 [16];
  undefined1 local_48 [64];
  
  DAT_140630c7c = 0;
  iVar2 = FUN_140112274(0x483,0xa00f);
  uVar1 = 0xa00f;
  if (iVar2 == 0) {
    iVar2 = FUN_140112274(0x483,0xa0cb);
    uVar1 = 0xa0cb;
    if (iVar2 == 0) {
      uVar3 = FUN_140111f40();
      if ((int)uVar3 != 0) {
        return uVar3;
      }
      (*DAT_14018c830)(DAT_140630f48,local_res10,local_res8,local_58,local_48,0);
      local_res8[0] = (local_res8[0] ^ 0xffffa5a5) & 0xffff;
      if (local_res8[0] == 0xc5a4) {
        DAT_140631efb = 1;
      }
      (*DAT_14018c820)(DAT_140630f48,8,0,0);
      (*DAT_14018c8b8)(DAT_140630f48,DAT_1405a5544);
      (*DAT_14018c840)(DAT_140630f48,3);
      uVar1 = DAT_140630c7c;
    }
  }
  DAT_140630c7c = uVar1;
  DAT_140631e84 = 1;
  return 0;
}


```

