/**
 * Tabs — underline tab strip used on Project Detail (9 tabs) and Run Detail
 * (6 tabs). Purple underline marks the active tab; no pill/background fill.
 *
 * @startingPoint section="Core" subtitle="Underline tab strip" viewport="500x70"
 */
export interface TabItem {
  key: string;
  label: string;
}
export interface TabsProps {
  items: TabItem[];
  activeKey: string;
  onChange: (key: string) => void;
}
